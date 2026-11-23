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
public class Tema {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nome;

    /** Todo tema pertence a um curso; o mesmo nome pode existir em cursos diferentes. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Curso curso;
}
