package com.estudaenade.api.api.dto;

import com.estudaenade.api.api.dto.AtividadeDtos.AtividadeResponse;
import com.estudaenade.api.api.dto.AtividadeDtos.TentativaResumoResponse;

import java.util.List;
import java.util.UUID;

public final class DesempenhoDtos {

    private DesempenhoDtos() {
    }

    public record TemaDesempenho(UUID temaId, String tema, long respondidas, long acertos, int percentual) {
        public TemaDesempenho(UUID temaId, String tema, long respondidas, long acertos) {
            this(temaId, tema, respondidas, acertos, DesempenhoDtos.percentual(acertos, respondidas));
        }
    }

    /** Desempenho geral do aluno: prática livre + tentativas finalizadas. */
    public record DesempenhoAlunoResponse(long respondidas, long acertos, Integer percentual, String melhorTema,
                                          List<TemaDesempenho> porTema) {
    }

    public record AtividadeRecente(UUID id, String titulo, String tipo, String status, int totalQuestoes,
                                   Integer duracaoMinutos, Integer duracao, UnidadeTempoResponse unidadeTempo,
                                   long concluiram) {
    }

    /** Painel inicial do professor para uma turma. */
    public record ResumoTurmaResponse(long totalAlunos, long questoesNoBanco, long atividadesAbertas,
                                      Integer mediaPercentual, List<AtividadeRecente> atividades) {
    }

    /** Turma ativa no painel inicial do professor. */
    public record PainelTurma(UUID id, String nome, String periodo, ItemResponse curso, long totalAlunos,
                              long atividadesAbertas, Integer mediaPercentual) {
    }

    /** Atividade recente no painel inicial, com a turma a que pertence. */
    public record AtividadeRecentePainel(UUID id, String titulo, String tipo, String status, int totalQuestoes,
                                         Integer duracaoMinutos, Integer duracao,
                                         UnidadeTempoResponse unidadeTempo, long concluiram, ItemResponse turma,
                                         long totalAlunos) {
    }

    /** Painel inicial do professor: todas as turmas ativas. */
    public record PainelProfessorResponse(long turmasAtivas, long totalAlunos, long atividadesAbertas,
                                          long questoesNoBanco, List<PainelTurma> turmas,
                                          List<AtividadeRecentePainel> atividades) {
    }

    /** Linha do aluno nos resultados da atividade. Sem tentativa finalizada, acertos/total/tempo vêm nulos. */
    public record ResultadoAluno(UUID alunoId, String nome, long tentativas, Integer acertos, Integer total,
                                 Long tempoSegundos) {
    }

    /**
     * Resultados de uma atividade. Quando há mais de uma tentativa, vale a melhor de cada aluno.
     *
     * @param media nota média de 0 a 10 entre os alunos que concluíram
     */
    public record ResultadosAtividadeResponse(AtividadeResponse atividade, int totalAlunos, int concluiram,
                                              Double media, Long tempoMedioSegundos,
                                              List<TemaDesempenho> porTema, List<ResultadoAluno> alunos) {
    }

    /** Desempenho de um aluno nas atividades de uma turma (visão do professor). */
    public record DesempenhoAlunoTurmaResponse(UUID alunoId, String nome, List<TemaDesempenho> porTema,
                                               List<TentativaResumoResponse> tentativas) {
    }

    public static int percentual(long acertos, long total) {
        return total == 0 ? 0 : (int) Math.round(acertos * 100.0 / total);
    }
}
