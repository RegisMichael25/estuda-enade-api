package com.estudaenade.api.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Convite de uso único que um professor aprovado entrega a um colega: quem se cadastra com ele já entra aprovado. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConviteProfessor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** O código em si só é mostrado uma vez, na criação. Aqui fica o hash. */
    @Column(nullable = false, unique = true)
    private String codigoHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario criadoPor;

    @Builder.Default
    @Column(nullable = false)
    private Instant criadoEm = Instant.now();

    @Column(nullable = false)
    private Instant expiraEm;

    /** Nulo = ainda não usado. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Usuario usadoPor;

    private Instant usadoEm;

    /** Nulo = não foi cancelado manualmente. */
    private Instant invalidadoEm;

    public boolean disponivel(Instant agora) {
        return usadoEm == null && invalidadoEm == null && expiraEm.isAfter(agora);
    }
}