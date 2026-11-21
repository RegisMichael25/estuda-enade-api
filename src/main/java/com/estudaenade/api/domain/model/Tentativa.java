package com.estudaenade.api.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

import com.estudaenade.api.domain.enums.Encerramento;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tentativa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Atividade atividade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario aluno;

    @Column(nullable = false)
    private int numero;

    @Column(nullable = false)
    private Instant iniciadaEm;

    /** Fim do cronômetro, definido no início. Nulo = atividade sem tempo. */
    private Instant expiraEm;

    /** Nulo = em andamento. */
    private Instant finalizadaEm;

    @Enumerated(EnumType.STRING)
    private Encerramento encerramento;

    private Integer acertos;

    @Column(nullable = false)
    private int total;

    public boolean finalizada() {
        return finalizadaEm != null;
    }
}
