package com.estudaenade.api.api;

import com.estudaenade.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Duração da atividade com unidade de tempo (minutos ou horas). */
class DuracaoDaAtividadeTest extends IntegrationTest {

    private String prof;
    private String turmaId;
    private String codigoConvite;
    private String questao;
    private String rota;

    @BeforeEach
    void preparar() throws Exception {
        prof = professor();
        ResultActions turma = criarTurma(prof);
        turmaId = ler(turma, "$.id");
        codigoConvite = ler(turma, "$.codigoConvite");
        String curso = criarCurso(prof, "ADS");
        questao = questaoPublicada(prof, curso, criarTema(prof, curso, "Redes"), 1);
        rota = "/api/v1/professor/turmas/" + turmaId + "/atividades";
    }

    /** Corpo de um simulado; {@code duracao} e {@code unidade} vão como estão no JSON (use "null" para nulo). */
    private String corpo(String duracao, String unidade) {
        return """
                {"titulo":"Simulado","tipo":"SIMULADO","duracao":%s,"unidadeTempoId":%s,"maxTentativas":1,"questaoIds":["%s"]}
                """.formatted(duracao, unidade, questao);
    }

    private static String id(String uuid) {
        return "\"" + uuid + "\"";
    }

    @Test
    void listaAsUnidadesFixasMinutosAntesDeHoras() throws Exception {
        get("/api/v1/unidades-tempo", aluno("Ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(MINUTOS_ID))
                .andExpect(jsonPath("$[0].codigo").value("min"))
                .andExpect(jsonPath("$[0].nome").value("Minutos"))
                .andExpect(jsonPath("$[0].descricao").isNotEmpty())
                .andExpect(jsonPath("$[1].id").value(HORAS_ID))
                .andExpect(jsonPath("$[1].codigo").value("h"))
                .andExpect(jsonPath("$[1].nome").value("Horas"));

        get("/api/v1/unidades-tempo", null).andExpect(status().isUnauthorized());
    }

    @Test
    void simuladoEmHorasConverteParaMinutosEmTodasAsRespostasENoCronometro() throws Exception {
        String atividade = ler(post(rota, prof, corpo("2", id(HORAS_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duracao").value(2))
                .andExpect(jsonPath("$.unidadeTempo.codigo").value("h"))
                .andExpect(jsonPath("$.duracaoMinutos").value(120)), "$.id");
        post("/api/v1/professor/atividades/" + atividade + "/publicar", prof, null).andExpect(status().isOk());

        get("/api/v1/professor/atividades/" + atividade, prof)
                .andExpect(jsonPath("$.atividade.duracaoMinutos").value(120))
                .andExpect(jsonPath("$.atividade.unidadeTempo.id").value(HORAS_ID));
        get("/api/v1/professor/painel", prof)
                .andExpect(jsonPath("$.atividades[0].duracao").value(2))
                .andExpect(jsonPath("$.atividades[0].unidadeTempo.codigo").value("h"))
                .andExpect(jsonPath("$.atividades[0].duracaoMinutos").value(120));
        get("/api/v1/professor/painel?turmaId=" + turmaId, prof)
                .andExpect(jsonPath("$.atividades[0].duracao").value(2))
                .andExpect(jsonPath("$.atividades[0].unidadeTempo.nome").value("Horas"))
                .andExpect(jsonPath("$.atividades[0].duracaoMinutos").value(120));

        String ana = aluno("Ana");
        post("/api/v1/aluno/turmas/entrar", ana, "{\"codigo\":\"%s\"}".formatted(codigoConvite)).andExpect(status().isOk());
        get("/api/v1/aluno/atividades", ana)
                .andExpect(jsonPath("$[0].duracao").value(2))
                .andExpect(jsonPath("$[0].unidadeTempo.codigo").value("h"))
                .andExpect(jsonPath("$[0].duracaoMinutos").value(120));

        ResultActions estado = post("/api/v1/aluno/atividades/" + atividade + "/tentativas", ana, null)
                .andExpect(status().isOk());
        Instant iniciada = Instant.parse(ler(estado, "$.iniciadaEm"));
        Instant expira = Instant.parse(ler(estado, "$.expiraEm"));
        assertEquals(Duration.ofHours(2), Duration.between(iniciada, expira));
    }

    @Test
    void duracaoEmMinutosContinuaEmMinutos() throws Exception {
        post(rota, prof, corpo("90", id(MINUTOS_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duracao").value(90))
                .andExpect(jsonPath("$.unidadeTempo.codigo").value("min"))
                .andExpect(jsonPath("$.duracaoMinutos").value(90));
    }

    @Test
    void duracaoSemUnidadeOuComUnidadeInexistenteERecusada() throws Exception {
        post(rota, prof, corpo("30", "null"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("UNIDADE_TEMPO_OBRIGATORIA"));
        post(rota, prof, corpo("30", id("00000000-0000-0000-0007-000000000099")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("UNIDADE_TEMPO_INVALIDA"));
    }

    @Test
    void limiteDeDezHorasValeParaOValorConvertido() throws Exception {
        post(rota, prof, corpo("10", id(HORAS_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duracaoMinutos").value(600));
        post(rota, prof, corpo("600", id(MINUTOS_ID))).andExpect(status().isCreated());

        post(rota, prof, corpo("11", id(HORAS_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DURACAO_INVALIDA"));
        post(rota, prof, corpo("600", id(HORAS_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DURACAO_INVALIDA"));

        // fora do contrato: zero ou acima de 600 na própria unidade
        post(rota, prof, corpo("0", id(MINUTOS_ID))).andExpect(status().isBadRequest());
        post(rota, prof, corpo("601", id(MINUTOS_ID))).andExpect(status().isBadRequest());
    }

    @Test
    void semDuracaoAUnidadeEIgnorada() throws Exception {
        post(rota, prof, corpo("null", id(HORAS_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duracao").value(nullValue()))
                .andExpect(jsonPath("$.unidadeTempo").value(nullValue()))
                .andExpect(jsonPath("$.duracaoMinutos").value(nullValue()));
    }

    @Test
    void edicaoDoRascunhoTrocaAUnidadeERemoveADuracao() throws Exception {
        String rascunho = ler(post(rota, prof, corpo("2", id(HORAS_ID))).andExpect(status().isCreated()), "$.id");

        put("/api/v1/professor/atividades/" + rascunho, prof, corpo("45", id(MINUTOS_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracao").value(45))
                .andExpect(jsonPath("$.unidadeTempo.codigo").value("min"))
                .andExpect(jsonPath("$.duracaoMinutos").value(45));

        put("/api/v1/professor/atividades/" + rascunho, prof, corpo("null", "null"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracao").value(nullValue()))
                .andExpect(jsonPath("$.unidadeTempo").value(nullValue()));
    }

    @Test
    void bancoRecusaDuracaoSemUnidade() throws Exception {
        String atividade = ler(post(rota, prof, corpo("30", id(MINUTOS_ID))).andExpect(status().isCreated()), "$.id");
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("update atividade set unidade_tempo_id = null where id = ?::uuid", atividade));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("update atividade set duracao = null where id = ?::uuid", atividade));
    }
}
