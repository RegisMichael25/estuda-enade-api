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

    /** Valor na unidade escolhida pelo professor. Nulo = sem tempo. */
    private Integer duracao;

    /** Unidade de {@link #duracao}. Nula quando não há duração (restrição no banco). */
    @ManyToOne(fetch = FetchType.LAZY)
    private UnidadeTempo unidadeTempo;

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

    /** Duração convertida para minutos; nula quando a atividade não tem tempo. */
    public Integer duracaoEmMinutos() {
        return duracao == null ? null : unidadeTempo.paraMinutos(duracao);
    }
}
