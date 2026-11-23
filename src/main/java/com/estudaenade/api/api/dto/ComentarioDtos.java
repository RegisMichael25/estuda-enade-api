package com.estudaenade.api.api.dto;

import com.estudaenade.api.domain.enums.Papel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Mural de comentários das atividades. A requisição é gerada do contrato OpenAPI (pacote {@code api.contrato.dto}). */
public final class ComentarioDtos {

    private ComentarioDtos() {
    }

    /** {@code meu}, {@code podeEditar} e {@code podeApagar} são calculados para quem fez a requisição. */
    public record ComentarioResponse(UUID id, String texto, ItemResponse autor, Papel papelAutor,
                                     Instant criadoEm, Instant editadoEm,
                                     boolean meu, boolean podeEditar, boolean podeApagar) {
    }

    public record MuralResponse(boolean podeComentar, List<ComentarioResponse> comentarios) {
    }
}
