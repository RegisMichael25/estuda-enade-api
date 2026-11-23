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

    /** Hash da senha ({@code PasswordEncoder}). Nulo = a conta ainda não tem senha. */
    private String senhaHash;

    private Instant senhaAlteradaEm;

    /** Identificador estável da conta Google vinculada (claim {@code sub}). */
    private String googleSub;

    /**
     * Verdadeiro quando o dono do e-mail foi comprovado (login com Google ou código por e-mail).
     * Hoje é só informativo: não aprova professor nem libera nada.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean emailVerificado = false;

    /** Senhas erradas seguidas; zera no acerto. */
    @Builder.Default
    @Column(nullable = false)
    private int falhasLogin = 0;

    /** Enquanto estiver no futuro, o login com senha é recusado. */
    private Instant bloqueadoAte;

    @Builder.Default
    @Column(nullable = false)
    private Instant criadoEm = Instant.now();
}
