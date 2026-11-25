package com.estudaenade.api.domain.service;

import com.estudaenade.api.api.dto.ItemResponse;
import com.estudaenade.api.api.dto.ProvaPessoalDtos.ProvaPessoalResponse;
import com.estudaenade.api.api.dto.ProvaPessoalDtos.ProvaPessoalResumoResponse;
import com.estudaenade.api.api.dto.ProvaPessoalDtos.QuestaoDaProvaResponse;
import com.estudaenade.api.api.dto.QuestaoDtos.GabaritoResponse;
import com.estudaenade.api.api.dto.QuestaoDtos.QuestaoAlunoResponse;
import com.estudaenade.api.domain.enums.StatusQuestao;
import com.estudaenade.api.domain.model.Alternativa;
import com.estudaenade.api.domain.model.ProvaPessoal;
import com.estudaenade.api.domain.model.ProvaPessoalQuestao;
import com.estudaenade.api.domain.model.Questao;
import com.estudaenade.api.domain.repository.ProvaPessoalRepository;
import com.estudaenade.api.domain.repository.QuestaoRepository;
import com.estudaenade.api.domain.repository.UsuarioRepository;
import com.estudaenade.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;

/**
 * Prova que o aluno monta na prática livre e pode retomar em qualquer aparelho. É só dele: não tem turma
 * nem atividade, e o professor não a vê. Cada resposta também é registrada como resposta de prática
 * ({@link PraticaService}), para contar no desempenho do próprio aluno.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProvaPessoalService {

    static final int TAMANHO_NOME = 80;

    private final ProvaPessoalRepository provaRepository;
    private final QuestaoRepository questaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PraticaService praticaService;

    public ProvaPessoalResponse criar(UUID alunoId, List<UUID> questaoIds, String nome) {
        List<UUID> ids = questaoIds == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(questaoIds));
        if (ids.isEmpty()) throw ApiException.regra("PROVA_VAZIA", "Escolha ao menos uma questão.");

        Set<UUID> reservadas = new HashSet<>(questaoRepository.reservadasParaAtividade(alunoId));
        Map<UUID, Questao> porId = new HashMap<>();
        questaoRepository.findAllById(ids).forEach(q -> porId.put(q.getId(), q));
        List<Questao> questoes = new ArrayList<>();
        for (UUID id : ids) {
            Questao q = porId.get(id);
            if (q == null || !disponivel(q, reservadas)) {
                throw ApiException.regra("QUESTAO_INDISPONIVEL",
                        "Uma das questões escolhidas não está mais disponível na prática livre. Monte a prova de novo.");
            }
            questoes.add(q);
        }
        if (questoes.stream().map(q -> q.getCurso().getId()).distinct().count() > 1) {
            throw ApiException.regra("CURSOS_DIFERENTES", "As questões da prova precisam ser do mesmo curso.");
        }
        return detalhe(salvar(alunoId, questoes, nome), reservadas);
    }

    @Transactional(readOnly = true)
    public List<ProvaPessoalResumoResponse> listar(UUID alunoId) {
        return provaRepository.findByAlunoIdOrderByCriadaEmDesc(alunoId).stream().map(ProvaPessoalService::resumo).toList();
    }

    @Transactional(readOnly = true)
    public ProvaPessoalResponse detalhar(UUID alunoId, UUID provaId) {
        return detalhe(doAluno(alunoId, provaId), reservadas(alunoId));
    }

    public GabaritoResponse responder(UUID alunoId, UUID provaId, UUID questaoId, UUID alternativaId) {
        ProvaPessoal prova = doAluno(alunoId, provaId);
        if (prova.concluida()) {
            throw ApiException.regra("PROVA_CONCLUIDA", "Esta prova já foi concluída. Use \"Refazer\" para praticar de novo.");
        }
        ProvaPessoalQuestao item = prova.getQuestoes().stream()
                .filter(i -> i.getQuestao().getId().equals(questaoId)).findFirst()
                .orElseThrow(() -> ApiException.naoEncontrado("Questão não encontrada nesta prova."));
        if (item.respondida()) {
            throw ApiException.regra("QUESTAO_JA_RESPONDIDA", "Você já respondeu esta questão nesta prova.");
        }
        // Valida a alternativa e a disponibilidade, e registra no desempenho do aluno
        GabaritoResponse gabarito = praticaService.responder(alunoId, questaoId, alternativaId);
        Alternativa marcada = item.getQuestao().getAlternativas().stream()
                .filter(a -> a.getId().equals(alternativaId)).findFirst().orElseThrow();
        item.setAlternativa(marcada);
        item.setCorreta(gabarito.acertou());
        item.setRespondidaEm(Instant.now());
        return gabarito;
    }

    public ProvaPessoalResponse concluir(UUID alunoId, UUID provaId) {
        ProvaPessoal prova = doAluno(alunoId, provaId);
        if (!prova.concluida()) prova.setConcluidaEm(Instant.now());
        return detalhe(prova, reservadas(alunoId));
    }

    /** Nova prova com as mesmas questões, na mesma ordem e sem respostas. */
    public ProvaPessoalResponse refazer(UUID alunoId, UUID provaId) {
        ProvaPessoal original = doAluno(alunoId, provaId);
        return copiar(alunoId, original, i -> true, original.getNome());
    }

    /** Nova prova só com as questões erradas ou sem resposta. */
    public ProvaPessoalResponse comErradas(UUID alunoId, UUID provaId) {
        ProvaPessoal original = doAluno(alunoId, provaId);
        return copiar(alunoId, original, i -> !Boolean.TRUE.equals(i.getCorreta()), "Erradas · " + original.getNome());
    }

    /** Some do histórico; as respostas continuam no desempenho. */
    public void excluir(UUID alunoId, UUID provaId) {
        provaRepository.delete(doAluno(alunoId, provaId));
    }

    // ======================== apoio ========================

    private ProvaPessoalResponse copiar(UUID alunoId, ProvaPessoal original, Predicate<ProvaPessoalQuestao> filtro, String nome) {
        Set<UUID> reservadas = reservadas(alunoId);
        List<Questao> questoes = original.getQuestoes().stream()
                .filter(filtro)
                .map(ProvaPessoalQuestao::getQuestao)
                .filter(q -> disponivel(q, reservadas))
                .toList();
        if (questoes.isEmpty()) {
            throw ApiException.regra("SEM_QUESTOES_DISPONIVEIS", "Nenhuma questão desta prova está disponível para praticar agora.");
        }
        return detalhe(salvar(alunoId, questoes, nome), reservadas);
    }

    private ProvaPessoal salvar(UUID alunoId, List<Questao> questoes, String nome) {
        ProvaPessoal prova = ProvaPessoal.builder()
                .aluno(usuarioRepository.getReferenceById(alunoId))
                .curso(questoes.getFirst().getCurso())
                .nome(nomeOuPadrao(nome, questoes))
                .build();
        for (int i = 0; i < questoes.size(); i++) {
            prova.getQuestoes().add(ProvaPessoalQuestao.builder().prova(prova).questao(questoes.get(i)).ordem(i).build());
        }
        return provaRepository.save(prova);
    }

    /** Sem nome, usa os temas: "Redes", "Redes e Banco de Dados" ou "Redes + 2 temas". */
    private static String nomeOuPadrao(String nome, List<Questao> questoes) {
        String limpo = nome == null ? "" : nome.trim();
        if (limpo.isEmpty()) {
            List<String> temas = questoes.stream().map(q -> q.getTema().getNome()).distinct().toList();
            limpo = switch (temas.size()) {
                case 1 -> temas.getFirst();
                case 2 -> temas.get(0) + " e " + temas.get(1);
                default -> temas.getFirst() + " + " + (temas.size() - 1) + " temas";
            };
        }
        return limpo.length() > TAMANHO_NOME ? limpo.substring(0, TAMANHO_NOME - 1).trim() + "…" : limpo;
    }

    private ProvaPessoal doAluno(UUID alunoId, UUID provaId) {
        return provaRepository.findByIdAndAlunoId(provaId, alunoId)
                .orElseThrow(() -> ApiException.naoEncontrado("Prova não encontrada."));
    }

    private Set<UUID> reservadas(UUID alunoId) {
        return new HashSet<>(questaoRepository.reservadasParaAtividade(alunoId));
    }

    /** Publicada e fora de atividade que o aluno ainda não concluiu (senão a prova entregaria o gabarito). */
    private static boolean disponivel(Questao q, Set<UUID> reservadas) {
        return q.getStatus() == StatusQuestao.PUBLICADA && !reservadas.contains(q.getId());
    }

    private static ProvaPessoalResumoResponse resumo(ProvaPessoal p) {
        List<ProvaPessoalQuestao> itens = p.getQuestoes();
        List<String> temas = itens.stream().map(i -> i.getQuestao().getTema().getNome()).distinct().toList();
        int respondidas = (int) itens.stream().filter(ProvaPessoalQuestao::respondida).count();
        int acertos = (int) itens.stream().filter(i -> Boolean.TRUE.equals(i.getCorreta())).count();
        return new ProvaPessoalResumoResponse(p.getId(), p.getNome(), ItemResponse.de(p.getCurso()), temas,
                itens.size(), respondidas, acertos, p.getCriadaEm(), p.getConcluidaEm());
    }

    private static ProvaPessoalResponse detalhe(ProvaPessoal p, Set<UUID> reservadas) {
        List<QuestaoDaProvaResponse> questoes = p.getQuestoes().stream().map(i -> {
            Questao q = i.getQuestao();
            boolean disponivel = disponivel(q, reservadas);
            GabaritoResponse gabarito = null;
            if (disponivel && i.respondida()) {
                Alternativa correta = q.alternativaCorreta();
                gabarito = new GabaritoResponse(q.getId(), correta.getId(), correta.letra(), q.getExplicacao(),
                        i.getAlternativa() == null ? null : i.getAlternativa().getId(), Boolean.TRUE.equals(i.getCorreta()));
            }
            return new QuestaoDaProvaResponse(i.getOrdem(), QuestaoAlunoResponse.de(q), disponivel, gabarito);
        }).toList();
        return new ProvaPessoalResponse(resumo(p), questoes);
    }
}
