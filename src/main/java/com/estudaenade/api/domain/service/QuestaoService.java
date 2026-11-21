package com.estudaenade.api.domain.service;

import com.estudaenade.api.api.contrato.dto.AlternativaRequest;
import com.estudaenade.api.api.contrato.dto.QuestaoRequest;
import com.estudaenade.api.api.dto.Pagina;
import com.estudaenade.api.api.dto.QuestaoDtos.*;
import com.estudaenade.api.domain.enums.Papel;
import com.estudaenade.api.domain.enums.StatusQuestao;
import com.estudaenade.api.domain.model.Alternativa;
import com.estudaenade.api.domain.model.Questao;
import com.estudaenade.api.domain.model.Sinalizacao;
import com.estudaenade.api.domain.repository.*;
import com.estudaenade.api.exception.ApiException;
import com.estudaenade.api.infra.security.UsuarioLogado;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Banco institucional de questões: cadastro, publicação, edição restrita ao autor e sinalizações. */
@Service
@RequiredArgsConstructor
@Transactional
public class QuestaoService {

    private final QuestaoRepository questaoRepository;
    private final SinalizacaoRepository sinalizacaoRepository;
    private final CursoRepository cursoRepository;
    private final TemaRepository temaRepository;
    private final UsuarioRepository usuarioRepository;

    /** Filtros do banco. Campos nulos não filtram. */
    public record Filtro(Boolean minhas, UUID cursoId, UUID temaId, Integer ano, StatusQuestao status,
                         Boolean sinalizadas) {
    }

    // ======================== professor ========================

    public QuestaoResponse criar(UUID autorId, QuestaoRequest req) {
        Questao questao = Questao.builder().autor(usuarioRepository.getReferenceById(autorId)).build();
        aplicar(questao, req);
        return QuestaoResponse.de(questaoRepository.save(questao), autorId);
    }

    /** Só o autor edita. Respostas já corrigidas não mudam se o gabarito for alterado depois. */
    public QuestaoResponse atualizar(UUID autorId, UUID questaoId, QuestaoRequest req) {
        Questao questao = doAutor(questaoId, autorId);
        aplicar(questao, req);
        if (questao.getStatus() == StatusQuestao.PUBLICADA) {
            validarParaPublicar(questao);
        }
        questao.setAtualizadaEm(Instant.now());
        return QuestaoResponse.de(questao, autorId);
    }

    /** Revisão feita pelo autor: a questão entra no banco compartilhado (ou volta, se estava oculta). */
    public QuestaoResponse publicar(UUID autorId, UUID questaoId) {
        Questao questao = doAutor(questaoId, autorId);
        validarParaPublicar(questao);
        questao.setStatus(StatusQuestao.PUBLICADA);
        if (questao.getPublicadaEm() == null) questao.setPublicadaEm(Instant.now());
        questao.setAtualizadaEm(Instant.now());
        return QuestaoResponse.de(questao, autorId);
    }

    /** Tira a questão do banco e da prática livre. Atividades que já a usam não mudam. */
    public QuestaoResponse ocultar(UUID autorId, UUID questaoId) {
        Questao questao = doAutor(questaoId, autorId);
        if (questao.getStatus() != StatusQuestao.PUBLICADA) {
            throw ApiException.regra("QUESTAO_NAO_PUBLICADA", "Só é possível ocultar uma questão publicada.");
        }
        questao.setStatus(StatusQuestao.OCULTA);
        questao.setAtualizadaEm(Instant.now());
        return QuestaoResponse.de(questao, autorId);
    }

    /** Banco visto pelo professor: questões publicadas de todos + as próprias em qualquer estado. */
    @Transactional(readOnly = true)
    public Pagina<QuestaoResponse> listar(UUID professorId, Filtro f, int pagina, int tamanho) {
        Specification<Questao> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            Predicate minha = cb.equal(root.get("autor").get("id"), professorId);
            if (Boolean.TRUE.equals(f.minhas())) {
                ps.add(minha);
            } else {
                ps.add(cb.or(minha, cb.equal(root.get("status"), StatusQuestao.PUBLICADA)));
            }
            if (f.status() != null) ps.add(cb.equal(root.get("status"), f.status()));
            if (Boolean.TRUE.equals(f.sinalizadas())) ps.add(cb.greaterThan(root.get("sinalizacoesAbertas"), 0L));
            filtrosComuns(ps, root, cb, f.cursoId(), f.temaId(), f.ano());
            return cb.and(ps.toArray(Predicate[]::new));
        };
        var page = questaoRepository.findAll(spec, pagina(pagina, tamanho, Sort.by(Sort.Direction.DESC, "atualizadaEm")));
        return Pagina.de(page, q -> QuestaoResponse.de(q, professorId));
    }

    @Transactional(readOnly = true)
    public QuestaoResponse detalhar(UUID professorId, UUID questaoId) {
        return QuestaoResponse.de(visivelAoProfessor(questaoId, professorId), professorId);
    }

    @Transactional(readOnly = true)
    public List<SinalizacaoResponse> sinalizacoes(UUID professorId, UUID questaoId) {
        visivelAoProfessor(questaoId, professorId);
        return sinalizacaoRepository.findByQuestaoIdOrderByCriadaEmDesc(questaoId).stream()
                .map(SinalizacaoResponse::de).toList();
    }

    /** O autor marca as sinalizações como tratadas (depois de corrigir a questão ou concluir que está certa). */
    public QuestaoResponse resolverSinalizacoes(UUID autorId, UUID questaoId) {
        doAutor(questaoId, autorId);
        sinalizacaoRepository.resolverAbertas(questaoId, Instant.now());
        return QuestaoResponse.de(doAutor(questaoId, autorId), autorId);
    }

    // ======================== aluno e professor ========================

    /** Marca a questão para revisão do autor. Não a remove do banco. */
    public void sinalizar(UsuarioLogado usuario, UUID questaoId, String motivo) {
        Questao questao = usuario.papel() == Papel.PROFESSOR
                ? visivelAoProfessor(questaoId, usuario.id())
                : publicada(questaoId);
        if (sinalizacaoRepository.existsByQuestaoIdAndUsuarioIdAndResolvidaFalse(questaoId, usuario.id())) {
            return; // já sinalizada por este usuário
        }
        sinalizacaoRepository.save(Sinalizacao.builder()
                .questao(questao)
                .usuario(usuarioRepository.getReferenceById(usuario.id()))
                .motivo(motivo == null || motivo.isBlank() ? null : motivo.trim())
                .build());
    }

    /**
     * Questões publicadas para a prática livre, sem gabarito. Ficam de fora as que o aluno ainda vai
     * responder em uma atividade: a prática devolve o gabarito na hora e entregaria a resposta da atividade.
     */
    @Transactional(readOnly = true)
    public Pagina<QuestaoAlunoResponse> listarParaPratica(UUID alunoId, UUID cursoId, UUID temaId, Integer ano,
                                                          int pagina, int tamanho) {
        List<UUID> reservadas = questaoRepository.reservadasParaAtividade(alunoId);
        Specification<Questao> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("status"), StatusQuestao.PUBLICADA));
            if (!reservadas.isEmpty()) ps.add(cb.not(root.get("id").in(reservadas)));
            filtrosComuns(ps, root, cb, cursoId, temaId, ano);
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Sort ordem = Sort.by(Sort.Order.desc("ano"), Sort.Order.asc("publicadaEm"), Sort.Order.asc("id"));
        return Pagina.de(questaoRepository.findAll(spec, pagina(pagina, tamanho, ordem)), QuestaoAlunoResponse::de);
    }

    /** Anos que têm questão publicada, para montar o filtro da prática livre. */
    @Transactional(readOnly = true)
    public List<Integer> anosDisponiveis() {
        return questaoRepository.findAll((root, query, cb) -> cb.equal(root.get("status"), StatusQuestao.PUBLICADA))
                .stream().map(Questao::getAno).distinct().sorted((a, b) -> b - a).toList();
    }

    public Questao publicada(UUID questaoId) {
        return questaoRepository.findById(questaoId)
                .filter(q -> q.getStatus() == StatusQuestao.PUBLICADA)
                .orElseThrow(() -> ApiException.naoEncontrado("Questão não encontrada."));
    }

    /** Questão publicada que o aluno pode responder na prática livre (não está reservada para uma atividade). */
    public Questao disponivelParaPratica(UUID alunoId, UUID questaoId) {
        Questao questao = publicada(questaoId);
        if (questaoRepository.reservadasParaAtividade(alunoId).contains(questaoId)) {
            throw ApiException.regra("QUESTAO_EM_ATIVIDADE",
                    "Esta questão faz parte de uma atividade que você ainda não concluiu. "
                            + "Ela volta para a prática livre depois que você enviar a atividade.");
        }
        return questao;
    }

    // ======================== apoio ========================

    private Questao visivelAoProfessor(UUID questaoId, UUID professorId) {
        return questaoRepository.findById(questaoId)
                .filter(q -> q.getStatus() == StatusQuestao.PUBLICADA || q.getAutor().getId().equals(professorId))
                .orElseThrow(() -> ApiException.naoEncontrado("Questão não encontrada."));
    }

    private Questao doAutor(UUID questaoId, UUID autorId) {
        Questao questao = visivelAoProfessor(questaoId, autorId);
        if (!questao.getAutor().getId().equals(autorId)) {
            throw ApiException.acessoNegado("Somente o autor pode alterar esta questão.");
        }
        return questao;
    }

    private void aplicar(Questao questao, QuestaoRequest req) {
        if (req.getAlternativas().stream().filter(QuestaoService::marcadaComoCorreta).count() > 1) {
            throw ApiException.regra("GABARITO_INVALIDO", "Marque apenas uma alternativa como correta.");
        }
        questao.setEnunciado(req.getEnunciado().trim());
        questao.setExplicacao(req.getExplicacao() == null || req.getExplicacao().isBlank() ? null : req.getExplicacao().trim());
        questao.setAno(req.getAno());
        questao.setCurso(cursoRepository.findById(req.getCursoId())
                .orElseThrow(() -> ApiException.naoEncontrado("Curso não encontrado.")));
        questao.setTema(temaRepository.findById(req.getTemaId())
                .orElseThrow(() -> ApiException.naoEncontrado("Tema não encontrado.")));

        // Atualiza as alternativas na mesma posição para manter os ids já usados em respostas
        List<Alternativa> atuais = questao.getAlternativas();
        for (int i = 0; i < req.getAlternativas().size(); i++) {
            AlternativaRequest nova = req.getAlternativas().get(i);
            if (i < atuais.size()) {
                atuais.get(i).setTexto(nova.getTexto().trim());
                atuais.get(i).setCorreta(marcadaComoCorreta(nova));
            } else {
                atuais.add(Alternativa.builder().questao(questao).ordem(i)
                        .texto(nova.getTexto().trim()).correta(marcadaComoCorreta(nova)).build());
            }
        }
        while (atuais.size() > req.getAlternativas().size()) {
            atuais.remove(atuais.size() - 1);
        }
    }

    /** {@code correta} ausente ou nulo na requisição conta como falso. */
    private static boolean marcadaComoCorreta(AlternativaRequest alternativa) {
        return Boolean.TRUE.equals(alternativa.getCorreta());
    }

    private void validarParaPublicar(Questao questao) {
        if (questao.alternativaCorreta() == null) {
            throw ApiException.regra("GABARITO_INVALIDO", "Marque a alternativa correta antes de publicar.");
        }
        if (questao.getExplicacao() == null) {
            throw ApiException.regra("EXPLICACAO_OBRIGATORIA", "Escreva a explicação antes de publicar.");
        }
    }

    private static void filtrosComuns(List<Predicate> ps, jakarta.persistence.criteria.Root<Questao> root,
                                      jakarta.persistence.criteria.CriteriaBuilder cb,
                                      UUID cursoId, UUID temaId, Integer ano) {
        if (cursoId != null) ps.add(cb.equal(root.get("curso").get("id"), cursoId));
        if (temaId != null) ps.add(cb.equal(root.get("tema").get("id"), temaId));
        if (ano != null) ps.add(cb.equal(root.get("ano"), ano));
    }

    private static PageRequest pagina(int pagina, int tamanho, Sort ordem) {
        return PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 100), ordem);
    }
}
