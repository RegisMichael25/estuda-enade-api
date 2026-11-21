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
public class Turma {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nome;

    /** Ex.: 2026.2 */
    private String periodo;

    @ManyToOne(fetch = FetchType.LAZY)
    private Curso curso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario professor;

    @Column(nullable = false, unique = true)
    private String codigoConvite;

    @Builder.Default
    @Column(nullable = false)
    private Instant criadaEm = Instant.now();
}
