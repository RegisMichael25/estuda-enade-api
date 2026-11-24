package com.estudaenade.api.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Unidade da duração de uma atividade. As linhas são fixas (V00009) e o código as reconhece por
 * {@link #codigo}. Toda conversão para minutos passa por {@link #minutos(String, int)}.
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnidadeTempo {

    public static final String MINUTOS = "min";
    public static final String HORAS = "h";

    /** Minutos em uma hora: a única constante de conversão do backend. */
    public static final int MINUTOS_POR_HORA = 60;

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String codigo;

    private String descricao;

    @Builder.Default
    @Column(nullable = false)
    private Instant criadaEm = Instant.now();

    @Builder.Default
    @Column(nullable = false)
    private Instant atualizadaEm = Instant.now();

    /** Converte um valor nesta unidade para minutos. */
    public int paraMinutos(int valor) {
        return minutos(codigo, valor);
    }

    /**
     * Converte um valor na unidade de {@code codigo} para minutos. Também usado pelo SQL nativo,
     * que lê só o código.
     *
     * @throws ArithmeticException se o resultado não cabe em {@code int}
     * @throws IllegalStateException se o código não é conhecido
     */
    public static int minutos(String codigo, int valor) {
        return switch (codigo) {
            case MINUTOS -> valor;
            case HORAS -> Math.multiplyExact(valor, MINUTOS_POR_HORA);
            default -> throw new IllegalStateException("Unidade de tempo desconhecida: " + codigo);
        };
    }
}
