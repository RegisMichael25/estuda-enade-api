package com.estudaenade.api.api;

import com.estudaenade.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthTest extends IntegrationTest {

    private static final String EMAIL = "ana@teste.dev";

    private void pedirCodigo() throws Exception {
        post("/api/v1/auth/codigo", null, "{\"email\":\"%s\"}".formatted(EMAIL)).andExpect(status().isNoContent());
    }

    @Test
    void primeiroAcessoPedeCadastroSemGastarOCodigo() throws Exception {
        pedirCodigo();
        post("/api/v1/auth/entrar", null, "{\"email\":\"%s\",\"codigo\":\"%s\"}".formatted(EMAIL, CODIGO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CADASTRO_NECESSARIO"));

        // O mesmo código ainda vale para concluir o cadastro
        ResultActions sessao = post("/api/v1/auth/entrar", null,
                "{\"email\":\"%s\",\"codigo\":\"%s\",\"nome\":\"Ana\",\"papel\":\"ALUNO\"}".formatted(EMAIL, CODIGO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.papel").value("ALUNO"));

        get("/api/v1/eu", ler(sessao, "$.accessToken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void codigoSoPodeSerUsadoUmaVez() throws Exception {
        pedirCodigo();
        String corpo = "{\"email\":\"%s\",\"codigo\":\"%s\",\"nome\":\"Ana\",\"papel\":\"ALUNO\"}".formatted(EMAIL, CODIGO);
        post("/api/v1/auth/entrar", null, corpo).andExpect(status().isOk());
        post("/api/v1/auth/entrar", null, corpo)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
    }

    @Test
    void codigoErradoERecusadoEQueimaAposCincoTentativas() throws Exception {
        pedirCodigo();
        for (int i = 0; i < 5; i++) {
            post("/api/v1/auth/entrar", null, "{\"email\":\"%s\",\"codigo\":\"000000\"}".formatted(EMAIL))
                    .andExpect(status().isUnauthorized());
        }
        // Depois de 5 erros nem o código certo funciona
        post("/api/v1/auth/entrar", null,
                "{\"email\":\"%s\",\"codigo\":\"%s\",\"nome\":\"Ana\",\"papel\":\"ALUNO\"}".formatted(EMAIL, CODIGO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void limitaPedidosDeCodigoPorEmail() throws Exception {
        pedirCodigo();
        pedirCodigo();
        pedirCodigo();
        post("/api/v1/auth/codigo", null, "{\"email\":\"%s\"}".formatted(EMAIL))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void refreshTokenGiraENaoPodeSerReutilizado() throws Exception {
        pedirCodigo();
        String refresh = ler(post("/api/v1/auth/entrar", null,
                "{\"email\":\"%s\",\"codigo\":\"%s\",\"nome\":\"Ana\",\"papel\":\"ALUNO\"}".formatted(EMAIL, CODIGO)), "$.refreshToken");

        String corpo = "{\"refreshToken\":\"%s\"}".formatted(refresh);
        post("/api/v1/auth/renovar", null, corpo).andExpect(status().isOk());
        post("/api/v1/auth/renovar", null, corpo).andExpect(status().isUnauthorized());
    }

    @Test
    void rotasExigemTokenEPapelCorreto() throws Exception {
        get("/api/v1/eu", null).andExpect(status().isUnauthorized());
        get("/api/v1/eu", "token-invalido").andExpect(status().isUnauthorized());
        get("/api/v1/professor/turmas", aluno("Ana")).andExpect(status().isForbidden());
        get("/api/v1/aluno/atividades", professor()).andExpect(status().isForbidden());
    }
}
