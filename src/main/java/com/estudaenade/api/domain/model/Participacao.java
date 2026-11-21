package com.estudaenade.api.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Vínculo de um aluno com uma turma. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Participacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Turma turma;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario aluno;

    @Builder.Default
    @Column(nullable = false)
    private Instant entrouEm = Instant.now();
}
