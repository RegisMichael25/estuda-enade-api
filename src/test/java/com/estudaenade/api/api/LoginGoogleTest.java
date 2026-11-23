package com.estudaenade.api.api;

import com.estudaenade.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static com.estudaenade.api.support.GoogleFalsoConfiguration.token;
import static com.estudaenade.api.support.GoogleFalsoConfiguration.tokenNaoVerificado;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login e cadastro com Google (verificador falso: veja {@code GoogleFalsoConfiguration}). A validação real do
 * token fica em {@code NimbusVerificadorTokenGoogleTest}.
 */
class LoginGoogleTest extends IntegrationTest {

    private static final String EMAIL = "ana@gmail.test";
    private static final String TOKEN = token("sub-ana", EMAIL, "Ana do Google");

    private ResultActions cadastrarAnaComGoogle(String nome) throws Exception {
        return cadastrarComGoogle(TOKEN, "ALUNO", nome, null);
    }

    @Test
    void tokenInvalidoOuComEmailNaoVerificadoERecusado() throws Exception {
        entrarComGoogle("lixo")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("GOOGLE_TOKEN_INVALIDO"));
        entrarComGoogle(tokenNaoVerificado("sub-ana", EMAIL, "Ana"))
                .andExpect(status().isUnauthorized());
        cadastrarComGoogle("lixo", "ALUNO", "Ana", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("GOOGLE_TOKEN_INVALIDO"));
    }

    @Test
    void googleSemContaPedeOCadastro() throws Exception {
        entrarComGoogle(TOKEN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CONTA_NAO_ENCONTRADA"));
    }

    @Test
    void cadastroComGoogleUsaONomeEditadoEOEmailDoToken() throws Exception {
        String id = ler(cadastroAna("Ana Editada"), "$.usuario.id");

        // o mesmo token (válido por cerca de 1 h) agora entra direto
        entrarComGoogle(TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.id").value(id));
    }

    private ResultActions cadastroAna(String nome) throws Exception {
        return cadastrarAnaComGoogle("  " + nome + " ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario.nome").value(nome))
                .andExpect(jsonPath("$.usuario.email").value(EMAIL))
                .andExpect(jsonPath("$.usuario.temSenha").value(true))
                .andExpect(jsonPath("$.usuario.googleVinculado").value(true));
    }

    @Test
    void quemSeCadastrouPeloGoogleTambemEntraComEmailESenha() throws Exception {
        cadastroAna("Ana");
        entrar(EMAIL, SENHA).andExpect(status().isOk());
    }

    @Test
    void cadastroComGoogleExigeSenhaValida() throws Exception {
        post("/api/v1/auth/google/cadastrar", null,
                "{\"idToken\":\"%s\",\"nome\":\"Ana\",\"papel\":\"ALUNO\"}".formatted(TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.senha").isNotEmpty());
        post("/api/v1/auth/google/cadastrar", null,
                "{\"idToken\":\"%s\",\"nome\":\"Ana\",\"senha\":\"somenteletras\",\"papel\":\"ALUNO\"}".formatted(TOKEN))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SENHA_FRACA"));
        // nada foi criado
        entrarComGoogle(TOKEN).andExpect(status().isNotFound());
    }

    @Test
    void cadastroComGoogleDeEmailJaUsadoDaConflito() throws Exception {
        cadastroAna("Ana");
        cadastrarAnaComGoogle("Ana")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMAIL_EM_USO"));

        cadastrar("bruno@gmail.test", "ALUNO", "Bruno", null).andExpect(status().isCreated());
        cadastrarComGoogle(token("sub-bruno", "Bruno@Gmail.test", "Bruno"), "ALUNO", "Bruno", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMAIL_EM_USO"));
    }

    @Test
    void contaComEmailComprovadoEntraPeloGoogleEMantemASenha() throws Exception {
        String refresh = ler(cadastrar(EMAIL, "ALUNO", "Ana", null), "$.refreshToken");
        // e-mail comprovado antes (ex.: conta antiga, que entrava por código, ou senha redefinida)
        jdbc.update("update usuario set email_verificado = true");

        entrarComGoogle(TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.googleVinculado").value(true))
                .andExpect(jsonPath("$.usuario.temSenha").value(true));
        entrar(EMAIL, SENHA).andExpect(status().isOk());
        post("/api/v1/auth/renovar", null, "{\"refreshToken\":\"%s\"}".formatted(refresh)).andExpect(status().isOk());
    }

    /**
     * Conta criada com senha (e-mail ainda não comprovado): entra pelo Google, passa a ter o e-mail comprovado
     * e mantém a senha e as sessões.
     */
    @Test
    void contaCriadaComSenhaEntraPeloGoogleEMantemASenhaEAsSessoes() throws Exception {
        String refresh = ler(cadastrar(EMAIL, "ALUNO", "Ana", null), "$.refreshToken");

        entrarComGoogle(TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.temSenha").value(true))
                .andExpect(jsonPath("$.usuario.googleVinculado").value(true));

        entrar(EMAIL, SENHA).andExpect(status().isOk());
        post("/api/v1/auth/renovar", null, "{\"refreshToken\":\"%s\"}".formatted(refresh)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select email_verificado from usuario", Boolean.class)).isTrue();
    }

    @Test
    void contaVinculadaEEncontradaPeloGoogleMesmoSeOEmailDoGoogleMudar() throws Exception {
        String id = ler(cadastroAna("Ana"), "$.usuario.id");
        entrarComGoogle(token("sub-ana", "ana.nova@gmail.test", "Ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.id").value(id));
    }
}
