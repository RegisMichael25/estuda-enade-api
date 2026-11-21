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
public class Alternativa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Questao questao;

    /** Posição na questão, a partir de 0 (0 = A, 1 = B...). */
    @Column(nullable = false)
    private int ordem;

    @Column(nullable = false)
    private String texto;

    @Column(nullable = false)
    private boolean correta;

    public String letra() {
        return String.valueOf((char) ('A' + ordem));
    }
}
