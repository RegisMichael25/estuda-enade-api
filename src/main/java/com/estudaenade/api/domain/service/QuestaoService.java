package com.estudaenade.api.domain.service;

import com.estudaenade.api.api.contrato.dto.AlternativaRequest;
import com.estudaenade.api.api.contrato.dto.QuestaoRequest;
import com.estudaenade.api.api.dto.ItemResponse;
import com.estudaenade.api.api.dto.Pagina;
import com.estudaenade.api.api.dto.QuestaoDtos.*;
import com.estudaenade.api.domain.enums.HistoricoPratica;
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
import java.util.Map;
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
    private final jakarta.persistence.EntityManager entityManager;

    /** Máximo de questões por sorteio na prática livre (o mesmo teto da paginação). */
    public static final int MAXIMO_SORTEIO = 100;

    /** Filtros do banco. Campos nulos (e busca em branco) não filtram. */
    public record Filtro(Boolean minhas, UUID cursoId, UUID temaId, Integer ano, String busca,
                         StatusQuestao status, Boolean sinalizadas) {
    }

    /** Letras acentuadas e as equivalentes sem acento, na mesma posição (para o {@code translate} do Postgres). */
    private static final String COM_ACENTO = "áàâãäéèêëíìîïóòôõöúùûüçñ";
    private static final String SEM_ACENTO = "aaaaaeeeeiiiiooooouuuucn";

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

    /** Exclui o rascunho de questão (só o autor). */
    public void excluirRascunho(UUID autorId, UUID questaoId) {
        Questao questao = doAutor(questaoId, autorId);
        if (questao.getStatus() != StatusQuestao.RASCUNHO) {
            throw ApiException.regra("QUESTAO_NAO_RASCUNHO", "Só é possível excluir questões em rascunho.");
        }
        questaoRepository.delete(questao);
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
            filtrosComuns(ps, root, cb, f.cursoId(), f.temaId(), f.ano(), f.busca());
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
     * Listas vazias (ou nulas) de temas e anos não filtram.
     */
    @Transactional(readOnly = true)
    public Pagina<QuestaoAlunoResponse> listarParaPratica(UUID alunoId, UUID cursoId, List<UUID> temaIds, List<Integer> anos,
                                                          HistoricoPratica historico, String busca, int pagina, int tamanho) {
        Specification<Questao> spec = disponivelNaPratica(alunoId, cursoId, temaIds, anos, historico, busca, List.of());
        Sort ordem = Sort.by(Sort.Order.desc("ano"), Sort.Order.asc("publicadaEm"), Sort.Order.asc("id"));
        return Pagina.de(questaoRepository.findAll(spec, pagina(pagina, tamanho, ordem)), QuestaoAlunoResponse::de);
    }

    /**
     * Quantas questões da prática há em cada tema do curso, para o aluno montar a prova. Só temas com questão.
     * O desempenho do aluno no tema vem de {@code desempenhoPorTema} (id do tema → [respondidas, acertos]).
     */
    @Transactional(readOnly = true)
    public List<ContagemTemaResponse> contagemDaPratica(UUID alunoId, UUID cursoId, List<Integer> anos,
                                                        HistoricoPratica historico, Map<UUID, long[]> desempenhoPorTema) {
        if (cursoId == null) throw ApiException.regra("CURSO_OBRIGATORIO", "Escolha o curso.");
        Specification<Questao> spec = disponivelNaPratica(alunoId, cursoId, List.of(), anos, historico, null, List.of());
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createTupleQuery();
        var root = query.from(Questao.class);
        var tema = root.join("tema");
        var total = cb.count(root);
        query.multiselect(tema.get("id"), tema.get("nome"), total)
                .where(spec.toPredicate(root, query, cb))
                .groupBy(tema.get("id"), tema.get("nome"))
                .orderBy(cb.asc(tema.get("nome")));
        return entityManager.createQuery(query).getResultList().stream()
                .map(t -> {
                    UUID temaId = t.get(0, UUID.class);
                    long[] d = desempenhoPorTema.getOrDefault(temaId, new long[]{0, 0});
                    return new ContagemTemaResponse(new ItemResponse(temaId, t.get(1, String.class)), t.get(2, Long.class),
                            d[0], d[1]);
                })
                .toList();
    }

    /**
     * Sorteia questões da prática para a prova que o aluno está montando, sem repetir as que ele já escolheu.
     * Com {@code equilibrar}, reparte a quantidade igualmente entre os temas (um de cada tema por vez).
     * Devolve menos que o pedido quando não há questões suficientes.
     */
    @Transactional(readOnly = true)
    public List<QuestaoAlunoResponse> sortearParaPratica(UUID alunoId, UUID cursoId, List<UUID> temaIds, List<Integer> anos,
                                                         HistoricoPratica historico, boolean equilibrar,
                                                         int quantidade, List<UUID> excluirIds) {
        if (cursoId == null) throw ApiException.regra("CURSO_OBRIGATORIO", "Escolha o curso.");
        if (quantidade < 1 || quantidade > MAXIMO_SORTEIO) {
            throw ApiException.regra("QUANTIDADE_INVALIDA", "Sorteie de 1 a " + MAXIMO_SORTEIO + " questões.");
        }
        Specification<Questao> spec = disponivelNaPratica(alunoId, cursoId, temaIds, anos, historico, null, excluirIds);
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createTupleQuery();
        var root = query.from(Questao.class);
        query.multiselect(root.get("id"), root.get("tema").get("id")).where(spec.toPredicate(root, query, cb));
        // Candidatas embaralhadas, agrupadas por tema
        Map<UUID, java.util.Deque<UUID>> porTema = new java.util.LinkedHashMap<>();
        List<jakarta.persistence.Tuple> candidatas = new ArrayList<>(entityManager.createQuery(query).getResultList());
        java.util.Collections.shuffle(candidatas);
        candidatas.forEach(t -> porTema.computeIfAbsent(t.get(1, UUID.class), k -> new java.util.ArrayDeque<>())
                .add(t.get(0, UUID.class)));

        List<UUID> sorteados = new ArrayList<>();
        if (equilibrar) {
            // Uma de cada tema por rodada, até completar ou acabar
            while (sorteados.size() < quantidade && porTema.values().stream().anyMatch(f -> !f.isEmpty())) {
                for (var fila : porTema.values()) {
                    if (sorteados.size() == quantidade) break;
                    if (!fila.isEmpty()) sorteados.add(fila.poll());
                }
            }
            java.util.Collections.shuffle(sorteados);
        } else {
            candidatas.stream().limit(quantidade).forEach(t -> sorteados.add(t.get(0, UUID.class)));
        }
        var porId = new java.util.HashMap<UUID, Questao>();
        questaoRepository.findAllById(sorteados).forEach(q -> porId.put(q.getId(), q));
        return sorteados.stream().map(porId::get).map(QuestaoAlunoResponse::de).toList();
    }

    /** Publicadas, sem as reservadas para atividade do aluno e sem as já escolhidas ({@code excluirIds}). */
    private Specification<Questao> disponivelNaPratica(UUID alunoId, UUID cursoId, List<UUID> temaIds, List<Integer> anos,
                                                       HistoricoPratica historico, String busca, List<UUID> excluirIds) {
        List<UUID> reservadas = new ArrayList<>(questaoRepository.reservadasParaAtividade(alunoId));
        if (excluirIds != null) reservadas.addAll(excluirIds);
        if (historico == HistoricoPratica.NAO_RESPONDIDAS) reservadas.addAll(questaoRepository.respondidasPeloAluno(alunoId));
        // "Só as que errei" restringe a uma lista; vazia, não sobra nenhuma questão
        List<UUID> somente = historico == HistoricoPratica.ERRADAS ? questaoRepository.erradasNaUltimaResposta(alunoId) : null;
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("status"), StatusQuestao.PUBLICADA));
            if (!reservadas.isEmpty()) ps.add(cb.not(root.get("id").in(reservadas)));
            if (somente != null) ps.add(somente.isEmpty() ? cb.disjunction() : root.get("id").in(somente));
            if (temaIds != null && !temaIds.isEmpty()) ps.add(root.get("tema").get("id").in(temaIds));
            if (anos != null && !anos.isEmpty()) ps.add(root.get("ano").in(anos));
            filtrosComuns(ps, root, cb, cursoId, null, null, busca);
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    /** Anos que têm questão publicada (no curso, se informado), para o filtro da prática livre. */
    @Transactional(readOnly = true)
    public List<Integer> anosDisponiveis(UUID cursoId) {
        return cursoId == null
                ? questaoRepository.anosComStatus(StatusQuestao.PUBLICADA)
                : questaoRepository.anosComStatusNoCurso(StatusQuestao.PUBLICADA, cursoId);
    }

    /** Anos das questões que o professor vê no banco (no curso, se informado), para o filtro do banco. */
    @Transactional(readOnly = true)
    public List<Integer> anosDoBanco(UUID professorId, UUID cursoId) {
        return cursoId == null
                ? questaoRepository.anosVisiveisAoProfessor(StatusQuestao.PUBLICADA, professorId)
                : questaoRepository.anosVisiveisAoProfessorNoCurso(StatusQuestao.PUBLICADA, professorId, cursoId);
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
        if (!questao.getTema().getCurso().getId().equals(questao.getCurso().getId())) {
            throw ApiException.regra("TEMA_DE_OUTRO_CURSO", "O tema escolhido não pertence ao curso da questão.");
        }

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
                                      UUID cursoId, UUID temaId, Integer ano, String busca) {
        if (cursoId != null) ps.add(cb.equal(root.get("curso").get("id"), cursoId));
        if (temaId != null) ps.add(cb.equal(root.get("tema").get("id"), temaId));
        if (ano != null) ps.add(cb.equal(root.get("ano"), ano));
        String trecho = normalizar(busca);
        if (!trecho.isEmpty()) {
            // Compara os dois lados sem maiúsculas e sem acento: "matematica" acha "Matemática"
            jakarta.persistence.criteria.Expression<String> enunciado = cb.function("translate", String.class,
                    cb.lower(root.get("enunciado")), cb.literal(COM_ACENTO), cb.literal(SEM_ACENTO));
            ps.add(cb.like(enunciado, "%" + escaparLike(trecho) + "%", '\\'));
        }
    }

    /** Minúsculas, sem acento e sem espaços nas pontas; {@code null} vira texto vazio. */
    private static String normalizar(String texto) {
        if (texto == null) return "";
        return java.text.Normalizer.normalize(texto.trim().toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    /** O que o usuário digita é texto literal: % e _ não viram curinga. */
    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static PageRequest pagina(int pagina, int tamanho, Sort ordem) {
        return PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 100), ordem);
    }
}
