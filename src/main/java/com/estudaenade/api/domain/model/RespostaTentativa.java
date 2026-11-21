package com.estudaenade.api.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RespostaTentativa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Tentativa tentativa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Questao questao;

    /** Nulo = questão deixada em branco. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Alternativa alternativa;

    /** Preenchido na correção, quando a tentativa é finalizada. */
    private Boolean correta;

    @Column(nullable = false)
    private Instant respondidaEm;
}
