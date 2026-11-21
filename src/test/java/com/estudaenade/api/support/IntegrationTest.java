package com.estudaenade.api.support;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integração: contexto Spring completo + PostgreSQL real (Testcontainers).
 * Os testes usam a API como um cliente usaria (HTTP + JSON); o banco é limpo antes de cada teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    /** Código fixo definido em application-test.properties. */
    protected static final String CODIGO = "123456";
    private static final AtomicInteger SEQUENCIA = new AtomicInteger(1);

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;

    @BeforeEach
    void limparBanco() {
        jdbc.execute("""
                TRUNCATE TABLE resposta_pratica, resposta_tentativa, tentativa, atividade_questao, atividade,
                               sinalizacao, alternativa, questao, participacao, turma, curso, tema,
                               refresh_token, codigo_acesso, convite_professor, usuario
                CASCADE
                """);
    }

    // ======================== HTTP ========================

    protected ResultActions get(String url, String token) throws Exception {
        return mvc.perform(autenticar(MockMvcRequestBuilders.get(url), token));
    }

    protected ResultActions post(String url, String token, String json) throws Exception {
        return mvc.perform(corpo(autenticar(MockMvcRequestBuilders.post(url), token), json));
    }

    protected ResultActions put(String url, String token, String json) throws Exception {
        return mvc.perform(corpo(autenticar(MockMvcRequestBuilders.put(url), token), json));
    }

    protected ResultActions delete(String url, String token) throws Exception {
        return mvc.perform(autenticar(MockMvcRequestBuilders.delete(url), token));
    }

    /** Lê um valor do JSON de resposta, ex.: {@code ler(resposta, "$.id")}. */
    protected static <T> T ler(ResultActions resposta, String caminho) throws Exception {
        return JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), caminho);
    }

    private static MockHttpServletRequestBuilder autenticar(MockHttpServletRequestBuilder req, String token) {
        return token == null ? req : req.header("Authorization", "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder corpo(MockHttpServletRequestBuilder req, String json) {
        return json == null ? req : req.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    // ======================== FIXTURES ========================

    /**
     * Cadastra um usuário pelo fluxo de código por e-mail e devolve o access token.
     * O e-mail é {@code @teste.dev}, domínio institucional nos testes: professor já entra aprovado.
     */
    protected String entrarComo(String papel, String nome) throws Exception {
        String email = "usuario" + SEQUENCIA.getAndIncrement() + "@teste.dev";
        return ler(cadastrar(email, papel, nome, null).andExpect(status().isOk()), "$.accessToken");
    }

    /** Pede o código e tenta o primeiro acesso com o e-mail dado. {@code convite} nulo = sem convite. */
    protected ResultActions cadastrar(String email, String papel, String nome, String convite) throws Exception {
        post("/api/v1/auth/codigo", null, "{\"email\":\"%s\"}".formatted(email)).andExpect(status().isNoContent());
        return entrar(email, papel, nome, convite);
    }

    /** Primeiro acesso reaproveitando um código já pedido (o código não é gasto quando o cadastro falha). */
    protected ResultActions entrar(String email, String papel, String nome, String convite) throws Exception {
        String campoConvite = convite == null ? "" : ",\"codigoConvite\":\"%s\"".formatted(convite);
        return post("/api/v1/auth/entrar", null,
                "{\"email\":\"%s\",\"codigo\":\"%s\",\"nome\":\"%s\",\"papel\":\"%s\"%s}"
                        .formatted(email, CODIGO, nome, papel, campoConvite));
    }

    protected String professor() throws Exception {
        return entrarComo("PROFESSOR", "Prof. Silva");
    }

    protected String aluno(String nome) throws Exception {
        return entrarComo("ALUNO", nome);
    }

    protected String criarCurso(String professor, String nome) throws Exception {
        return ler(post("/api/v1/professor/cursos", professor, "{\"nome\":\"%s\"}".formatted(nome)).andExpect(status().isOk()), "$.id");
    }

    protected String criarTema(String professor, String nome) throws Exception {
        return ler(post("/api/v1/professor/temas", professor, "{\"nome\":\"%s\"}".formatted(nome)).andExpect(status().isOk()), "$.id");
    }

    /** Cria a turma e devolve a resposta (use {@code ler(r, "$.id")} e {@code "$.codigoConvite"}). */
    protected ResultActions criarTurma(String professor) throws Exception {
        return post("/api/v1/professor/turmas", professor, "{\"nome\":\"ADS · Turma A\",\"periodo\":\"2026.2\"}")
                .andExpect(status().isCreated());
    }

    /** Questão em rascunho com 4 alternativas; a correta é a de índice {@code correta} (0 = A). */
    protected ResultActions criarQuestao(String professor, String cursoId, String temaId, int correta) throws Exception {
        StringBuilder alternativas = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i > 0) alternativas.append(',');
            alternativas.append("{\"texto\":\"Alternativa %d\",\"correta\":%s}".formatted(i, i == correta));
        }
        return post("/api/v1/professor/questoes", professor, """
                {"enunciado":"Qual forma normal elimina dependências transitivas?",
                 "explicacao":"A 3FN exige que nenhum atributo não-chave dependa de outro.",
                 "ano":2021,"cursoId":"%s","temaId":"%s","alternativas":[%s]}
                """.formatted(cursoId, temaId, alternativas)).andExpect(status().isCreated());
    }

    /** Cria e publica a questão; devolve o id. */
    protected String questaoPublicada(String professor, String cursoId, String temaId, int correta) throws Exception {
        String id = ler(criarQuestao(professor, cursoId, temaId, correta), "$.id");
        post("/api/v1/professor/questoes/" + id + "/publicar", professor, null).andExpect(status().isOk());
        return id;
    }
}
