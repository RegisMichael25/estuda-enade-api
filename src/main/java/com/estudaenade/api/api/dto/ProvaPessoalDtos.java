package com.estudaenade.api.api.dto;

import com.estudaenade.api.api.dto.QuestaoDtos.GabaritoResponse;
import com.estudaenade.api.api.dto.QuestaoDtos.QuestaoAlunoResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Respostas da prova que o aluno monta na prática livre. */
public final class ProvaPessoalDtos {

    private ProvaPessoalDtos() {
    }

    /** Linha do histórico "Minhas provas". */
    public record ProvaPessoalResumoResponse(UUID id, String nome, ItemResponse curso, List<String> temas,
                                             int total, int respondidas, int acertos,
                                             Instant criadaEm, Instant concluidaEm) {
    }

    /** Questão da prova. O gabarito só vem se ela já foi respondida e continua disponível. */
    public record QuestaoDaProvaResponse(int ordem, QuestaoAlunoResponse questao, boolean disponivel,
                                         GabaritoResponse gabarito) {
    }

    public record ProvaPessoalResponse(ProvaPessoalResumoResponse prova, List<QuestaoDaProvaResponse> questoes) {
    }
}
