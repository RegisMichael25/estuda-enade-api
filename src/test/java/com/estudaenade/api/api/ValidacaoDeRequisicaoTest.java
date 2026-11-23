package com.estudaenade.api.api;

import com.estudaenade.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** As regras de validação vêm do contrato OpenAPI e chegam às classes de requisição geradas. */
class ValidacaoDeRequisicaoTest extends IntegrationTest {

    @Test
    void campoObrigatorioAusenteOuSoComEspacosERecusado() throws Exception {
        String prof = professor();
        post("/api/v1/professor/turmas", prof, "{\"nome\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos.nome").isNotEmpty());
        post("/api/v1/professor/turmas", prof, "{\"periodo\":\"2026.2\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").isNotEmpty());
        post("/api/v1/professor/turmas", prof, "{\"nome\":\"%s\"}".formatted("x".repeat(121)))
                .andExpect(status().isBadRequest());
        get("/api/v1/professor/turmas", prof).andExpect(jsonPath("$", hasSize(0)));
    }

    /** O contrato marca todos os campos das respostas como obrigatórios: os vazios precisam vir como null. */
    @Test
    void camposVaziosDaRespostaVemComoNullENaoSomem() throws Exception {
        String prof = professor();
        post("/api/v1/professor/turmas", prof, "{\"nome\":\"Turma sem período\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasKey("periodo")))
                .andExpect(jsonPath("$", hasKey("curso")))
                .andExpect(jsonPath("$.periodo").value(nullValue()));
    }

    @Test
    void emailInvalidoEPapelDesconhecidoSaoRecusados() throws Exception {
        post("/api/v1/auth/senha/codigo", null, "{\"email\":\"nao-e-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
        post("/api/v1/auth/entrar", null, "{\"email\":\"nao-e-email\",\"senha\":\"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.email").isNotEmpty());
        post("/api/v1/auth/cadastrar", null,
                "{\"nome\":\"A\",\"email\":\"a@teste.dev\",\"senha\":\"%s\",\"papel\":\"ADMIN\"}".formatted(SENHA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));
        post("/api/v1/auth/cadastrar", null, "{\"nome\":\"   \",\"email\":\"a@teste.dev\",\"senha\":\"%s\",\"papel\":\"ALUNO\"}"
                .formatted(SENHA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").isNotEmpty());
    }

    @Test
    void questaoPrecisaDeDuasACincoAlternativasComTexto() throws Exception {
        String prof = professor();
        String curso = criarCurso(prof, "ADS");
        String tema = criarTema(prof, curso, "Redes");
        String modelo = "{\"enunciado\":\"E\",\"ano\":2021,\"cursoId\":\"%s\",\"temaId\":\"%s\",\"alternativas\":[%s]}";

        post("/api/v1/professor/questoes", prof, modelo.formatted(curso, tema, "{\"texto\":\"única\",\"correta\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.alternativas").isNotEmpty());
        post("/api/v1/professor/questoes", prof, modelo.formatted(curso, tema, "{\"texto\":\"a\"},{\"texto\":\" \"}"))
                .andExpect(status().isBadRequest());
        // "correta" omitido vale falso
        post("/api/v1/professor/questoes", prof, modelo.formatted(curso, tema, "{\"texto\":\"a\"},{\"texto\":\"b\",\"correta\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alternativas[0].correta").value(false))
                .andExpect(jsonPath("$.alternativas[1].correta").value(true));
    }

    @Test
    void paginacaoForaDoLimiteEAjustadaEQuestaoNulaNaAtividadeERecusada() throws Exception {
        String prof = professor();
        get("/api/v1/professor/questoes?tamanho=500&pagina=-3", prof)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamanho").value(100))
                .andExpect(jsonPath("$.pagina").value(0));
        get("/api/v1/professor/questoes?status=INEXISTENTE", prof)
                .andExpect(status().isBadRequest());

        String turma = ler(criarTurma(prof), "$.id");
        post("/api/v1/professor/turmas/" + turma + "/atividades", prof,
                "{\"titulo\":\"Lista\",\"tipo\":\"LISTA\",\"questaoIds\":[null]}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("QUESTAO_INDISPONIVEL"));
    }
}
