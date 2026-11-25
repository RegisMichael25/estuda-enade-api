package com.estudaenade.api.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Prova montada pelo aluno na prática livre. Só dele: não tem turma nem atividade. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvaPessoal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario aluno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Curso curso;

    @Column(nullable = false, length = 80)
    private String nome;

    @Builder.Default
    @Column(nullable = false)
    private Instant criadaEm = Instant.now();

    /** Nulo = em andamento. */
    private Instant concluidaEm;

    @Builder.Default
    @OneToMany(mappedBy = "prova", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem")
    private List<ProvaPessoalQuestao> questoes = new ArrayList<>();

    public boolean concluida() {
        return concluidaEm != null;
    }
}
