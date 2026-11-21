package com.estudaenade.api.api;

import com.estudaenade.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A documentação é pública e mostra o arquivo de contrato; o resto da API continua exigindo token. */
class SwaggerTest extends IntegrationTest {

    @Test
    void swaggerUiAbreSemLoginEApontaParaOContrato() throws Exception {
        get("/swagger-ui.html", null).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/swagger-ui/**"));
        get("/swagger-ui/index.html", null).andExpect(status().isOk());
        get("/v3/api-docs/swagger-config", null)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/openapi/estuda-enade-api.json")));
    }

    @Test
    void contratoEServidoComoArquivoEstatico() throws Exception {
        get("/openapi/estuda-enade-api.json", null)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"openapi\"")))
                .andExpect(content().string(containsString("/api/v1/auth/entrar")));
    }

    @Test
    void liberarADocumentacaoNaoAbreAsRotasDaApi() throws Exception {
        get("/api/v1/eu", null).andExpect(status().isUnauthorized());
        get("/api/v1/professor/questoes", null).andExpect(status().isUnauthorized());
        get("/v3/api-docs", null).andExpect(status().isUnauthorized());
    }
}
