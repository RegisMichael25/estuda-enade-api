package com.estudaenade.api.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

import com.estudaenade.api.domain.enums.StatusQuestao;
import org.hibernate.annotations.Formula;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Questao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario autor;

    @Column(nullable = false)
    private String enunciado;

    private String explicacao;

    @Column(nullable = false)
    private Integer ano;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Curso curso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Tema tema;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusQuestao status = StatusQuestao.RASCUNHO;

    @Builder.Default
    @Column(nullable = false)
    private Instant criadaEm = Instant.now();

    @Builder.Default
    @Column(nullable = false)
    private Instant atualizadaEm = Instant.now();

    private Instant publicadaEm;

    @Builder.Default
    @OneToMany(mappedBy = "questao", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem")
    private List<Alternativa> alternativas = new ArrayList<>();

    /** Sinalizações de possível erro ainda não resolvidas pelo autor. */
    @Formula("(select count(*) from sinalizacao s where s.questao_id = id and s.resolvida = false)")
    private long sinalizacoesAbertas;

    public Alternativa alternativaCorreta() {
        return alternativas.stream().filter(Alternativa::isCorreta).findFirst().orElse(null);
    }
}
