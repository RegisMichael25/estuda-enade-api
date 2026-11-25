package com.estudaenade.api.api.dto;

import com.estudaenade.api.api.dto.DesempenhoDtos.TemaDesempenho;
import com.estudaenade.api.api.dto.QuestaoDtos.AlternativaResponse;
import com.estudaenade.api.api.dto.QuestaoDtos.QuestaoAlunoResponse;
import com.estudaenade.api.api.dto.QuestaoDtos.QuestaoResponse;
import com.estudaenade.api.domain.enums.Encerramento;
import com.estudaenade.api.domain.enums.StatusAtividade;
import com.estudaenade.api.domain.enums.TipoAtividade;
import com.estudaenade.api.domain.model.Atividade;
import com.estudaenade.api.domain.model.Tentativa;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Respostas desta área. As requisições são geradas do contrato OpenAPI (pacote {@code api.contrato.dto}). */
public final class AtividadeDtos {

    private AtividadeDtos() {
    }

    // ---------- professor ----------

    /**
     * @param duracaoMinutos duração já convertida para minutos
     * @param duracao        valor digitado pelo professor, na unidade de {@code unidadeTempo}
     */
    public record AtividadeResponse(UUID id, UUID turmaId, String titulo, TipoAtividade tipo,
                                    Integer duracaoMinutos, Integer duracao, UnidadeTempoResponse unidadeTempo,
                                    Integer maxTentativas, StatusAtividade status,
                                    int totalQuestoes, Instant criadaEm, Instant publicadaEm) {
        public static AtividadeResponse de(Atividade a) {
            return new AtividadeResponse(a.getId(), a.getTurma().getId(), a.getTitulo(), a.getTipo(),
                    a.duracaoEmMinutos(), a.getDuracao(), UnidadeTempoResponse.de(a.getUnidadeTempo()),
                    a.getMaxTentativas(), a.getStatus(),
                    a.getQuestoes().size(), a.getCriadaEm(), a.getPublicadaEm());
        }
    }

    public record AtividadeDetalheResponse(AtividadeResponse atividade, List<QuestaoResponse> questoes) {
    }

    // ---------- aluno ----------

    public record AtividadeAlunoResponse(UUID id, String titulo, TipoAtividade tipo, ItemResponse turma,
                                         int totalQuestoes, Integer duracaoMinutos, Integer duracao,
                                         UnidadeTempoResponse unidadeTempo, Integer maxTentativas,
                                         StatusAtividade status, long tentativasFeitas, UUID tentativaAbertaId,
                                         Integer melhorAcertos, UUID melhorTentativaId, boolean podeIniciar) {
    }

    /**
     * Estado de uma tentativa em andamento. O cronômetro é do servidor: o cliente calcula o tempo
     * restante por {@code expiraEm - agora} (ou usa {@code segundosRestantes}) e não por relógio próprio.
     *
     * @param respostas questaoId → alternativaId já marcada
     */
    public record TentativaEstadoResponse(UUID id, UUID atividadeId, String titulo, int numero,
                                          Instant iniciadaEm, Instant expiraEm, Instant agora,
                                          Long segundosRestantes, boolean finalizada,
                                          List<QuestaoAlunoResponse> questoes, Map<UUID, UUID> respostas) {
    }

    public record TentativaResumoResponse(UUID id, UUID atividadeId, String titulo, int numero,
                                          Instant iniciadaEm, Instant finalizadaEm, Encerramento encerramento,
                                          Integer acertos, int total) {
        public static TentativaResumoResponse de(Tentativa t) {
            return new TentativaResumoResponse(t.getId(), t.getAtividade().getId(), t.getAtividade().getTitulo(),
                    t.getNumero(), t.getIniciadaEm(), t.getFinalizadaEm(), t.getEncerramento(),
                    t.getAcertos(), t.getTotal());
        }
    }

    public record QuestaoCorrigidaResponse(UUID id, String enunciado, String explicacao, ItemResponse tema,
                                           List<AlternativaResponse> alternativas,
                                           UUID alternativaMarcadaId, boolean acertou) {
    }

    /** Resultado de uma tentativa finalizada, com gabarito e explicações. */
    public record ResultadoTentativaResponse(TentativaResumoResponse tentativa, long tempoSegundos,
                                             List<TemaDesempenho> porTema,
                                             List<QuestaoCorrigidaResponse> questoes) {
    }
}
