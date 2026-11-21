package com.estudaenade.api.api.dto;

import com.estudaenade.api.domain.model.ConviteProfessor;
import com.estudaenade.api.domain.model.Usuario;

import java.time.Instant;
import java.util.UUID;

public final class CadastroProfessorDtos {

    private CadastroProfessorDtos() {
    }

    /** Professor que se cadastrou sem convite e sem e-mail institucional e aguarda aprovação. */
    public record ProfessorPendenteResponse(UUID id, String nome, String email, boolean aprovado, Instant criadoEm) {
        public static ProfessorPendenteResponse de(Usuario u) {
            return new ProfessorPendenteResponse(u.getId(), u.getNome(), u.getEmail(), u.isAprovado(), u.getCriadoEm());
        }
    }

    /** Resposta da criação: é a única vez em que o código aparece. */
    public record ConviteCriadoResponse(UUID id, String codigo, Instant expiraEm) {
    }

    /** Convite na listagem de quem criou, sem o código. */
    public record ConviteResponse(UUID id, Instant criadoEm, Instant expiraEm, boolean usado, String usadoPor,
                                  Instant usadoEm) {
        public static ConviteResponse de(ConviteProfessor c) {
            return new ConviteResponse(c.getId(), c.getCriadoEm(), c.getExpiraEm(), c.getUsadoEm() != null,
                    c.getUsadoPor() == null ? null : c.getUsadoPor().getNome(), c.getUsadoEm());
        }
    }
}
