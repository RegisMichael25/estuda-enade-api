package com.estudaenade.api.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

import com.estudaenade.api.domain.enums.StatusAtividade;
import com.estudaenade.api.domain.enums.TipoAtividade;

import java.util.ArrayList;
import java.util.List;

/** Lista ou simulado publicado pelo professor para uma turma. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Atividade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Turma turma;

    @Column(nullable = false)
    private String titulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoAtividade tipo;

    /** Nulo = sem tempo. */
    private Integer duracaoMinutos;

    /** Nulo = tentativas ilimitadas. */
    private Integer maxTentativas;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusAtividade status = StatusAtividade.RASCUNHO;

    @Builder.Default
    @Column(nullable = false)
    private Instant criadaEm = Instant.now();

    private Instant publicadaEm;

    @Builder.Default
    @OneToMany(mappedBy = "atividade", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem")
    private List<AtividadeQuestao> questoes = new ArrayList<>();
}
