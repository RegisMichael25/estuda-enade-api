package com.estudaenade.api.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Questão de uma prova do aluno, com a resposta dada (nula enquanto ele não responde). */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvaPessoalQuestao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ProvaPessoal prova;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Questao questao;

    @Column(nullable = false)
    private int ordem;

    @ManyToOne(fetch = FetchType.LAZY)
    private Alternativa alternativa;

    /** Nulo = ainda sem resposta. */
    private Boolean correta;

    private Instant respondidaEm;

    public boolean respondida() {
        return respondidaEm != null;
    }
}
