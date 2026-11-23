package com.estudaenade.api.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

import com.estudaenade.api.domain.enums.Papel;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Papel papel;

    /**
     * Só importa para professor: enquanto for falso, ele não acessa as rotas do professor.
     * Aluno é sempre criado com {@code true}.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean aprovado = false;

    /** Preenchidos quando a aprovação foi dada por outro professor. */
    private Instant aprovadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    private Usuario aprovadoPor;

    /** Nulo: conta sem senha. */
    private String senhaHash;

    private Instant senhaAlteradaEm;

    /** Claim {@code sub} da conta Google vinculada. */
    private String googleSub;

    /** E-mail comprovado (Google ou código). Só informativo. */
    @Builder.Default
    @Column(nullable = false)
    private boolean emailVerificado = false;

    /** Senhas erradas seguidas. */
    @Builder.Default
    @Column(nullable = false)
    private int falhasLogin = 0;

    private Instant bloqueadoAte;

    @Builder.Default
    @Column(nullable = false)
    private Instant criadoEm = Instant.now();
}
