package com.estudaenade.api.domain.enums;

/** Filtro da prática livre pelo que o aluno já respondeu (na prática ou em atividades já enviadas). */
public enum HistoricoPratica {
    /** Sem filtro. */
    TODAS,
    /** Questões que o aluno nunca respondeu. */
    NAO_RESPONDIDAS,
    /** Questões cuja última resposta do aluno foi errada. */
    ERRADAS
}
