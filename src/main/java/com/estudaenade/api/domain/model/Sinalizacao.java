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
public class Sinalizacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Questao questao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario usuario;

    private String motivo;

    @Builder.Default
    @Column(nullable = false)
    private boolean resolvida = false;

    @Builder.Default
    @Column(nullable = false)
    private Instant criadaEm = Instant.now();

    private Instant resolvidaEm;
}
