package com.estudaenade.api.api;

import com.estudaenade.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BancoDeQuestoesTest extends IntegrationTest {

    private String autor;
    private String outroProfessor;
    private String curso;
    private String tema;

    @BeforeEach
    void preparar() throws Exception {
        autor = professor();
        outroProfessor = entrarComo("PROFESSOR", "Prof. Lima");
        curso = criarCurso(autor, "ADS");
        tema = criarTema(autor, curso, "Banco de Dados");
    }

    @Test
    void rascunhoSoApareceParaOAutorEPublicadaEntraNoBancoCompartilhado() throws Exception {
        String id = ler(criarQuestao(autor, curso, tema, 2), "$.id");

        get("/api/v1/professor/questoes", outroProfessor).andExpect(jsonPath("$.total").value(0));
        get("/api/v1/professor/questoes/" + id, outroProfessor).andExpect(status().isNotFound());
        get("/api/v1/aluno/pratica/questoes", aluno("Ana")).andExpect(jsonPath("$.total").value(0));

        post("/api/v1/professor/questoes/" + id + "/publicar", autor, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLICADA"));

        get("/api/v1/professor/questoes", outroProfessor)
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].minha").value(false));
    }

    @Test
    void naoPublicaSemGabarito() throws Exception {
        String id = ler(criarQuestao(autor, curso, tema, -1), "$.id");
        post("/api/v1/professor/questoes/" + id + "/publicar", autor, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("GABARITO_INVALIDO"));
    }

    @Test
    void somenteOAutorEdita() throws Exception {
        String id = questaoPublicada(autor, curso, tema, 2);
        String corpo = """
                {"enunciado":"Alterado","explicacao":"x","ano":2024,"cursoId":"%s","temaId":"%s",
                 "alternativas":[{"texto":"a","correta":true},{"texto":"b","correta":false}]}
                """.formatted(curso, tema);

        put("/api/v1/professor/questoes/" + id, outroProfessor, corpo).andExpect(status().isForbidden());
        put("/api/v1/professor/questoes/" + id, autor, corpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enunciado").value("Alterado"))
                .andExpect(jsonPath("$.alternativas", hasSize(2)));
    }

    @Test
    void sinalizacaoMarcaParaRevisaoSemRemoverDoBanco() throws Exception {
        String id = questaoPublicada(autor, curso, tema, 2);
        String ana = aluno("Ana");

        post("/api/v1/aluno/questoes/" + id + "/sinalizacoes", ana, "{\"motivo\":\"Gabarito parece errado\"}")
                .andExpect(status().isNoContent());
        post("/api/v1/aluno/questoes/" + id + "/sinalizacoes", ana, null).andExpect(status().isNoContent()); // repetida: ignora

        get("/api/v1/professor/questoes?sinalizadas=true", autor)
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].sinalizacoesAbertas").value(1));
        get("/api/v1/aluno/pratica/questoes", ana).andExpect(jsonPath("$.total").value(1)); // continua disponível

        // a lista mostra o motivo, mas não identifica quem sinalizou
        get("/api/v1/professor/questoes/" + id + "/sinalizacoes", outroProfessor)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].motivo").value("Gabarito parece errado"))
                .andExpect(jsonPath("$[0].autor").doesNotExist());

        post("/api/v1/professor/questoes/" + id + "/sinalizacoes/resolver", outroProfessor, null).andExpect(status().isForbidden());
        post("/api/v1/professor/questoes/" + id + "/sinalizacoes/resolver", autor, null)
                .andExpect(jsonPath("$.sinalizacoesAbertas").value(0));
    }

    @Test
    void praticaLivreNaoEntregaGabaritoAntesDaResposta() throws Exception {
        String id = questaoPublicada(autor, curso, tema, 2);
        String ana = aluno("Ana");

        ResultActions lista = get("/api/v1/aluno/pratica/questoes?temaId=" + tema + "&ano=2021", ana)
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].alternativas", hasSize(4)))
                .andExpect(jsonPath("$.itens[0].alternativas[0].correta").doesNotExist())
                .andExpect(jsonPath("$.itens[0].explicacao").doesNotExist());
        String errada = ler(lista, "$.itens[0].alternativas[0].id");
        String certa = ler(lista, "$.itens[0].alternativas[2].id");

        post("/api/v1/aluno/pratica/questoes/" + id + "/responder", ana, "{\"alternativaId\":\"%s\"}".formatted(errada))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acertou").value(false))
                .andExpect(jsonPath("$.letraCorreta").value("C"))
                .andExpect(jsonPath("$.alternativaCorretaId").value(certa))
                .andExpect(jsonPath("$.explicacao").isNotEmpty());
        post("/api/v1/aluno/pratica/questoes/" + id + "/responder", ana, "{\"alternativaId\":\"%s\"}".formatted(certa))
                .andExpect(jsonPath("$.acertou").value(true));

        get("/api/v1/aluno/desempenho", ana)
                .andExpect(jsonPath("$.respondidas").value(2))
                .andExpect(jsonPath("$.acertos").value(1))
                .andExpect(jsonPath("$.percentual").value(50))
                .andExpect(jsonPath("$.porTema[0].tema").value("Banco de Dados"));
    }

    @Test
    void questaoOcultaSaiDaPraticaLivre() throws Exception {
        String id = questaoPublicada(autor, curso, tema, 2);
        post("/api/v1/professor/questoes/" + id + "/ocultar", autor, null).andExpect(status().isOk());
        get("/api/v1/aluno/pratica/questoes", aluno("Ana")).andExpect(jsonPath("$.total").value(0));
        get("/api/v1/professor/questoes", outroProfessor).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void temaPertenceAoCursoEMesmoNomePodeExistirEmOutroCurso() throws Exception {
        String outroCurso = criarCurso(autor, "Engenharia");
        String temaOutroCurso = criarTema(autor, outroCurso, "Banco de Dados");

        assertNotEquals(tema, temaOutroCurso);
        assertEquals(tema, criarTema(autor, curso, "banco de dados"));

        get("/api/v1/temas?cursoId=" + curso, autor)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(tema));
        get("/api/v1/temas", autor).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void naoCriaTemaEmCursoInexistente() throws Exception {
        post("/api/v1/professor/temas", autor,
                "{\"nome\":\"Redes\",\"cursoId\":\"00000000-0000-0000-0000-00000000ffff\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void questaoNaoAceitaTemaDeOutroCurso() throws Exception {
        String outroCurso = criarCurso(autor, "Engenharia");
        post("/api/v1/professor/questoes", autor, """
                {"enunciado":"E","ano":2021,"cursoId":"%s","temaId":"%s",
                 "alternativas":[{"texto":"a","correta":true},{"texto":"b","correta":false}]}
                """.formatted(outroCurso, tema))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TEMA_DE_OUTRO_CURSO"));
    }
}
