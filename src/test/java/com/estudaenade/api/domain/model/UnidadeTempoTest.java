package com.estudaenade.api.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnidadeTempoTest {

    private static UnidadeTempo unidade(String codigo) {
        return UnidadeTempo.builder().codigo(codigo).nome(codigo).build();
    }

    @Test
    void minutosNaoMudamEHorasViramSessentaMinutos() {
        assertEquals(1, UnidadeTempo.minutos(UnidadeTempo.MINUTOS, 1));
        assertEquals(90, UnidadeTempo.minutos(UnidadeTempo.MINUTOS, 90));
        assertEquals(60, UnidadeTempo.minutos(UnidadeTempo.HORAS, 1));
        assertEquals(600, UnidadeTempo.minutos(UnidadeTempo.HORAS, 10));
        assertEquals(120, unidade(UnidadeTempo.HORAS).paraMinutos(2));
    }

    @Test
    void codigoDesconhecidoEErroEnaoViraMinutos() {
        assertThrows(IllegalStateException.class, () -> UnidadeTempo.minutos("d", 1));
    }

    @Test
    void horasQueNaoCabemEmIntEstouramEmVezDeVirarNegativo() {
        assertThrows(ArithmeticException.class, () -> UnidadeTempo.minutos(UnidadeTempo.HORAS, Integer.MAX_VALUE));
    }

    @Test
    void atividadeSemDuracaoNaoTemMinutos() {
        assertNull(new Atividade().duracaoEmMinutos());
        Atividade comDuasHoras = Atividade.builder().duracao(2).unidadeTempo(unidade(UnidadeTempo.HORAS)).build();
        assertEquals(120, comDuasHoras.duracaoEmMinutos());
    }
}
