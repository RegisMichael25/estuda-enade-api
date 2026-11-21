package com.estudaenade.api.api.dto;

import com.estudaenade.api.domain.model.Curso;
import com.estudaenade.api.domain.model.Tema;

import java.util.UUID;

/** Par id + nome, usado para curso, tema e outras referências curtas. */
public record ItemResponse(UUID id, String nome) {

    public static ItemResponse de(Curso curso) {
        return curso == null ? null : new ItemResponse(curso.getId(), curso.getNome());
    }

    public static ItemResponse de(Tema tema) {
        return tema == null ? null : new ItemResponse(tema.getId(), tema.getNome());
    }
}
