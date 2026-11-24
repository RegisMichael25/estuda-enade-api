package com.estudaenade.api.api.dto;

import com.estudaenade.api.domain.model.UnidadeTempo;

import java.util.UUID;

/** Unidade da duração de uma atividade. Os campos de auditoria não saem na API. */
public record UnidadeTempoResponse(UUID id, String nome, String codigo, String descricao) {

    public static UnidadeTempoResponse de(UnidadeTempo u) {
        return u == null ? null : new UnidadeTempoResponse(u.getId(), u.getNome(), u.getCodigo(), u.getDescricao());
    }
}
