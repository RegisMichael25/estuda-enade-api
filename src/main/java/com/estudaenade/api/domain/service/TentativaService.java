package com.estudaenade.api.domain.service;

import com.estudaenade.api.api.dto.AtividadeDtos.*;
import com.estudaenade.api.api.dto.ItemResponse;
import com.estudaenade.api.api.dto.QuestaoDtos.AlternativaResponse;
import com.estudaenade.api.api.dto.QuestaoDtos.QuestaoAlunoResponse;
import com.estudaenade.api.api.dto.UnidadeTempoResponse;
import com.estudaenade.api.domain.enums.Encerramento;
import com.estudaenade.api.domain.enums.StatusAtividade;
import com.estudaenade.api.domain.model.*;
import com.estudaenade.api.domain.repository.*;
import com.estudaenade.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Atividades vistas pelo aluno e suas tentativas.
 *
 * <p>O cronômetro é do servidor: {@code expiraEm} é fixado no início e não pausa. As respostas são gravadas
 * a cada marcação, então fechar o app, perder a conexão ou trocar de aparelho não perde nada — o aluno
 * retoma a mesma tentativa. Ao fim do tempo, o que estiver marcado é enviado automaticamente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TentativaService {

    private static final List<StatusAtividade> VISIVEIS_AO_ALUNO = List.of(StatusAtividade.PUBLICADA, StatusAtividade.ENCERRADA);

    private final AtividadeRepository atividadeRepository;
    private final TentativaRepository tentativaRepository;
    private final RespostaTentativaRepository respostaRepository;
    private final ParticipacaoRepository participacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final DesempenhoService desempenhoService;

    // ======================== atividades ========================

    public List<AtividadeAlunoResponse> listarAtividades(UUID alunoId) {
        List<UUID> turmaIds = participacaoRepository.findByAlunoIdOrderByEntrouEmDesc(alunoId).stream()
                .map(p -> p.getTurma().getId()).toList();
        if (turmaIds.isEmpty()) return List.of();

        Map<UUID, List<Tentativa>> porAtividade = tentativaRepository.findByAlunoIdOrderByIniciadaEmDesc(alunoId).stream()
                .peek(this::sincronizar)
                .collect(Collectors.groupingBy(t -> t.getAtividade().getId()));

        return atividadeRepository.findByTurmaIdInAndStatusInOrderByPublicadaEmDesc(turmaIds, VISIVEIS_AO_ALUNO).stream()
                .map(a -> resumo(a, porAtividade.getOrDefault(a.getId(), List.of())))
                .toList();
    }

    public AtividadeAlunoResponse detalharAtividade(UUID alunoId, UUID atividadeId) {
        Atividade atividade = doAluno(atividadeId, alunoId);
        List<Tentativa> tentativas = tentativaRepository.findByAlunoIdOrderByIniciadaEmDesc(alunoId).stream()
                .filter(t -> t.getAtividade().getId().equals(atividadeId))
                .peek(this::sincronizar)
                .toList();
        return resumo(atividade, tentativas);
    }

    // ======================== tentativas ========================

    /** Inicia uma tentativa e dispara o cronômetro. Se já houver uma em andamento, devolve a mesma (retomada). */
    public TentativaEstadoResponse iniciar(UUID alunoId, UUID atividadeId) {
        Atividade atividade = doAluno(atividadeId, alunoId);

        Optional<Tentativa> aberta = tentativaRepository.findByAtividadeIdAndAlunoIdAndFinalizadaEmIsNull(atividadeId, alunoId);
        if (aberta.isPresent()) {
            sincronizar(aberta.get());
            if (!aberta.get().finalizada()) return estado(aberta.get());
        }
        if (atividade.getStatus() != StatusAtividade.PUBLICADA) {
            throw ApiException.regra("ATIVIDADE_ENCERRADA", "Esta atividade foi encerrada pelo professor.");
        }
        long feitas = tentativaRepository.countByAtividadeIdAndAlunoId(atividadeId, alunoId);
        if (atividade.getMaxTentativas() != null && feitas >= atividade.getMaxTentativas()) {
            throw ApiException.regra("SEM_TENTATIVAS", "Você já usou todas as tentativas desta atividade.");
        }

        Instant agora = Instant.now();
        Tentativa tentativa = tentativaRepository.save(Tentativa.builder()
                .atividade(atividade)
                .aluno(usuarioRepository.getReferenceById(alunoId))
                .numero((int) feitas + 1)
                .iniciadaEm(agora)
                .expiraEm(atividade.getDuracao() == null ? null
                        : agora.plus(Duration.ofMinutes(atividade.duracaoEmMinutos())))
                .total(atividade.getQuestoes().size())
                .build());
        return estado(tentativa);
    }

    /** Questões (sem gabarito), respostas já marcadas e tempo restante. */
    public TentativaEstadoResponse estado(UUID alunoId, UUID tentativaId) {
        Tentativa tentativa = doAluno(tentativaId, alunoId, true);
        return estado(tentativa);
    }

    /** Marca, troca ou limpa ({@code alternativaId} nulo) a resposta de uma questão. */
    public void responder(UUID alunoId, UUID tentativaId, UUID questaoId, UUID alternativaId) {
        Tentativa tentativa = doAluno(tentativaId, alunoId, true);
        if (tentativa.finalizada()) {
            throw ApiException.regra("TENTATIVA_ENCERRADA", "Esta tentativa já foi encerrada.");
        }
        Questao questao = tentativa.getAtividade().getQuestoes().stream()
                .map(AtividadeQuestao::getQuestao)
                .filter(q -> q.getId().equals(questaoId)).findFirst()
                .orElseThrow(() -> ApiException.naoEncontrado("Questão não faz parte desta atividade."));

        Optional<RespostaTentativa> existente = respostaRepository.findByTentativaIdAndQuestaoId(tentativaId, questaoId);
        if (alternativaId == null) {
            existente.ifPresent(respostaRepository::delete);
            return;
        }
        Alternativa alternativa = questao.getAlternativas().stream()
                .filter(a -> a.getId().equals(alternativaId)).findFirst()
                .orElseThrow(() -> ApiException.regra("ALTERNATIVA_INVALIDA", "A alternativa não pertence a esta questão."));

        RespostaTentativa resposta = existente.orElseGet(() ->
                RespostaTentativa.builder().tentativa(tentativa).questao(questao).build());
        resposta.setAlternativa(alternativa);
        resposta.setRespondidaEm(Instant.now());
        respostaRepository.save(resposta);
    }

    /** Envia as respostas e corrige. Repetir o envio devolve o mesmo resultado. */
    public ResultadoTentativaResponse enviar(UUID alunoId, UUID tentativaId) {
        Tentativa tentativa = doAluno(tentativaId, alunoId, true);
        if (!tentativa.finalizada()) {
            finalizar(tentativa, Encerramento.ENVIO, Instant.now());
        }
        return montarResultado(tentativa);
    }

    /** Gabarito, explicações e desempenho por tema — só depois que a tentativa termina. */
    public ResultadoTentativaResponse resultado(UUID alunoId, UUID tentativaId) {
        Tentativa tentativa = doAluno(tentativaId, alunoId, true);
        if (!tentativa.finalizada()) {
            throw ApiException.regra("TENTATIVA_EM_ANDAMENTO", "O gabarito fica disponível depois que você concluir a atividade.");
        }
        return montarResultado(tentativa);
    }

    public List<TentativaResumoResponse> historico(UUID alunoId) {
        return tentativaRepository.findByAlunoIdOrderByIniciadaEmDesc(alunoId).stream()
                .peek(this::sincronizar)
                .map(TentativaResumoResponse::de).toList();
    }

    /** Envio automático das tentativas cujo tempo acabou, mesmo que o aluno não volte ao app. */
    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT30S")
    public void finalizarExpiradas() {
        List<Tentativa> expiradas = tentativaRepository.findByFinalizadaEmIsNullAndExpiraEmLessThanEqual(Instant.now());
        expiradas.forEach(this::sincronizar);
        if (!expiradas.isEmpty()) log.info("{} tentativa(s) enviada(s) automaticamente por fim do tempo", expiradas.size());
    }

    // ======================== apoio ========================

    /** Se o tempo acabou e a tentativa ainda está aberta, encerra no instante exato da expiração. */
    private void sincronizar(Tentativa tentativa) {
        if (!tentativa.finalizada() && tentativa.getExpiraEm() != null && !Instant.now().isBefore(tentativa.getExpiraEm())) {
            finalizar(tentativa, Encerramento.TEMPO, tentativa.getExpiraEm());
        }
    }

    /** Corrige a tentativa. Questões em branco viram respostas erradas, para entrarem no desempenho por tema. */
    private void finalizar(Tentativa tentativa, Encerramento encerramento, Instant momento) {
        Map<UUID, RespostaTentativa> respostas = respostasPorQuestao(tentativa.getId());
        int acertos = 0;
        for (AtividadeQuestao aq : tentativa.getAtividade().getQuestoes()) {
            RespostaTentativa resposta = respostas.get(aq.getQuestao().getId());
            if (resposta == null) {
                resposta = RespostaTentativa.builder()
                        .tentativa(tentativa).questao(aq.getQuestao()).respondidaEm(momento).build();
            }
            boolean correta = resposta.getAlternativa() != null && resposta.getAlternativa().isCorreta();
            resposta.setCorreta(correta);
            respostaRepository.save(resposta);
            if (correta) acertos++;
        }
        tentativa.setAcertos(acertos);
        tentativa.setEncerramento(encerramento);
        tentativa.setFinalizadaEm(momento);
        tentativaRepository.saveAndFlush(tentativa);
    }

    private TentativaEstadoResponse estado(Tentativa tentativa) {
        Instant agora = Instant.now();
        Long restantes = tentativa.getExpiraEm() == null || tentativa.finalizada() ? null
                : Math.max(0, Duration.between(agora, tentativa.getExpiraEm()).toSeconds());
        // Depois de finalizada, o conteúdo vem pelo resultado (com gabarito)
        List<QuestaoAlunoResponse> questoes = tentativa.finalizada() ? List.of()
                : tentativa.getAtividade().getQuestoes().stream().map(aq -> QuestaoAlunoResponse.de(aq.getQuestao())).toList();
        Map<UUID, UUID> marcadas = new LinkedHashMap<>();
        respostasPorQuestao(tentativa.getId()).forEach((questaoId, r) -> {
            if (r.getAlternativa() != null) marcadas.put(questaoId, r.getAlternativa().getId());
        });
        return new TentativaEstadoResponse(tentativa.getId(), tentativa.getAtividade().getId(),
                tentativa.getAtividade().getTitulo(), tentativa.getNumero(), tentativa.getIniciadaEm(),
                tentativa.getExpiraEm(), agora, restantes, tentativa.finalizada(), questoes, marcadas);
    }

    private ResultadoTentativaResponse montarResultado(Tentativa tentativa) {
        Map<UUID, RespostaTentativa> respostas = respostasPorQuestao(tentativa.getId());
        List<QuestaoCorrigidaResponse> questoes = tentativa.getAtividade().getQuestoes().stream().map(aq -> {
            Questao q = aq.getQuestao();
            RespostaTentativa r = respostas.get(q.getId());
            return new QuestaoCorrigidaResponse(q.getId(), q.getEnunciado(), q.getExplicacao(), ItemResponse.de(q.getTema()),
                    q.getAlternativas().stream().map(AlternativaResponse::de).toList(),
                    r == null || r.getAlternativa() == null ? null : r.getAlternativa().getId(),
                    r != null && Boolean.TRUE.equals(r.getCorreta()));
        }).toList();
        return new ResultadoTentativaResponse(TentativaResumoResponse.de(tentativa),
                Duration.between(tentativa.getIniciadaEm(), tentativa.getFinalizadaEm()).toSeconds(),
                desempenhoService.porTemaDaTentativa(tentativa.getId()), questoes);
    }

    private Map<UUID, RespostaTentativa> respostasPorQuestao(UUID tentativaId) {
        return respostaRepository.findByTentativaId(tentativaId).stream()
                .collect(Collectors.toMap(r -> r.getQuestao().getId(), Function.identity()));
    }

    private AtividadeAlunoResponse resumo(Atividade a, List<Tentativa> tentativas) {
        UUID abertaId = tentativas.stream().filter(t -> !t.finalizada()).map(Tentativa::getId).findFirst().orElse(null);
        Integer melhor = tentativas.stream().filter(Tentativa::finalizada).map(Tentativa::getAcertos)
                .max(Comparator.naturalOrder()).orElse(null);
        boolean podeIniciar = abertaId != null || (a.getStatus() == StatusAtividade.PUBLICADA
                && (a.getMaxTentativas() == null || tentativas.size() < a.getMaxTentativas()));
        return new AtividadeAlunoResponse(a.getId(), a.getTitulo(), a.getTipo(),
                new ItemResponse(a.getTurma().getId(), a.getTurma().getNome()),
                a.getQuestoes().size(), a.duracaoEmMinutos(), a.getDuracao(),
                UnidadeTempoResponse.de(a.getUnidadeTempo()), a.getMaxTentativas(), a.getStatus(),
                tentativas.size(), abertaId, melhor, podeIniciar);
    }

    /** Atividade publicada (ou encerrada) de uma turma em que o aluno está, ou 404. */
    private Atividade doAluno(UUID atividadeId, UUID alunoId) {
        return atividadeRepository.findById(atividadeId)
                .filter(a -> VISIVEIS_AO_ALUNO.contains(a.getStatus()))
                .filter(a -> participacaoRepository.existsByTurmaIdAndAlunoId(a.getTurma().getId(), alunoId))
                .orElseThrow(() -> ApiException.naoEncontrado("Atividade não encontrada."));
    }

    private Tentativa doAluno(UUID tentativaId, UUID alunoId, boolean sincronizar) {
        Tentativa tentativa = tentativaRepository.findByIdAndAlunoId(tentativaId, alunoId)
                .orElseThrow(() -> ApiException.naoEncontrado("Tentativa não encontrada."));
        if (sincronizar) sincronizar(tentativa);
        return tentativa;
    }
}
