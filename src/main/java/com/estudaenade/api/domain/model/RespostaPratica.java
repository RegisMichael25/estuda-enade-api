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
public class RespostaPratica {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario aluno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Questao questao;

    @ManyToOne(fetch = FetchType.LAZY)
    private Alternativa alternativa;

    @Column(nullable = false)
    private boolean correta;

    @Builder.Default
    @Column(nullable = false)
    private Instant respondidaEm = Instant.now();
}
