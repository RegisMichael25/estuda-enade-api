package com.estudaenade.api.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Comentário no mural de uma atividade, escrito pelo professor da turma ou por um aluno. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComentarioAtividade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Atividade atividade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario autor;

    @Column(nullable = false)
    private String texto;

    @Builder.Default
    @Column(nullable = false)
    private Instant criadoEm = Instant.now();

    /** Nulo enquanto o texto não foi editado. */
    private Instant editadoEm;
}
