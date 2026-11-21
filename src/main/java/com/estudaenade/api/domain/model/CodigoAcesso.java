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
public class CodigoAcesso {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String codigoHash;

    @Column(nullable = false)
    private Instant expiraEm;

    @Builder.Default
    @Column(nullable = false)
    private boolean usado = false;

    @Builder.Default
    @Column(nullable = false)
    private int tentativas = 0;

    @Builder.Default
    @Column(nullable = false)
    private Instant criadoEm = Instant.now();
}
