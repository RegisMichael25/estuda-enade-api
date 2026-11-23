package com.estudaenade.api.api;

import com.estudaenade.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthTest extends IntegrationTest {

    private static final String EMAIL = "ana@teste.dev";
    private static final String NOVA_SENHA = "outra-senha-2";

    private ResultActions cadastrarAna() throws Exception {
        return cadastrar(EMAIL, "ALUNO", "Ana", null).andExpect(status().isCreated());
    }

    private ResultActions cadastrarComSenha(String senha) throws Exception {
        return post("/api/v1/auth/cadastrar", null,
                "{\"nome\":\"Ana\",\"email\":\"%s\",\"senha\":\"%s\",\"papel\":\"ALUNO\"}".formatted(EMAIL, senha));
    }

    private void pedirCodigo(String email) throws Exception {
        post("/api/v1/auth/senha/codigo", null, "{\"email\":\"%s\"}".formatted(email)).andExpect(status().isNoContent());
    }

    private ResultActions redefinir(String email, String codigo, String novaSenha) throws Exception {
        return post("/api/v1/auth/senha/redefinir", null,
                "{\"email\":\"%s\",\"codigo\":\"%s\",\"novaSenha\":\"%s\"}".formatted(email, codigo, novaSenha));
    }

    private void pedirCodigoTroca(String token) throws Exception {
        post("/api/v1/eu/senha/codigo", token, null).andExpect(status().isNoContent());
    }

    private ResultActions trocarSenha(String token, String codigo, String novaSenha) throws Exception {
        return put("/api/v1/eu/senha", token, "{\"codigo\":\"%s\",\"novaSenha\":\"%s\"}".formatted(codigo, novaSenha));
    }

    private ResultActions renovar(String refresh) throws Exception {
        return post("/api/v1/auth/renovar", null, "{\"refreshToken\":\"%s\"}".formatted(refresh));
    }

    // ======================== cadastro ========================

    @Test
    void cadastroComSenhaAbreSessao() throws Exception {
        ResultActions sessao = cadastrarAna()
                .andExpect(jsonPath("$.usuario.papel").value("ALUNO"))
                .andExpect(jsonPath("$.usuario.aprovado").value(true))
                .andExpect(jsonPath("$.usuario.temSenha").value(true))
                .andExpect(jsonPath("$.usuario.googleVinculado").value(false));

        get("/api/v1/eu", ler(sessao, "$.accessToken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
        String hash = jdbc.queryForObject("select senha_hash from usuario where email = ?", String.class, EMAIL);
        assertThat(hash).startsWith("{bcrypt}").doesNotContain(SENHA);
    }

    @Test
    void emailRepetidoNoCadastroDaConflitoMesmoComMaiusculas() throws Exception {
        cadastrarAna();
        cadastrar("ANA@Teste.dev", "PROFESSOR", "Outra Ana", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMAIL_EM_USO"));
    }

    @Test
    void senhaForaDaPoliticaERecusada() throws Exception {
        cadastrarComSenha("somenteletras").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SENHA_FRACA"));
        cadastrarComSenha("1234567890").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SENHA_FRACA"));
        // curta: barrada na validação do contrato
        cadastrarComSenha("abc12").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.senha").isNotEmpty());
        // 38 caracteres, mas 74 bytes
        cadastrarComSenha("a1" + "é".repeat(36)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SENHA_FRACA"));
        cadastrarComSenha("a1" + "é".repeat(35)).andExpect(status().isCreated());
    }

    @Test
    void alunoIgnoraOCodigoDeConviteDeProfessor() throws Exception {
        String professor = professor();
        String convite = ler(post("/api/v1/professor/convites", professor, null), "$.codigo");

        cadastrar(EMAIL, "ALUNO", "Ana", convite)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario.papel").value("ALUNO"))
                .andExpect(jsonPath("$.usuario.aprovado").value(true));
        get("/api/v1/professor/convites", professor).andExpect(jsonPath("$[0].usado").value(false));
    }

    // ======================== login ========================

    @Test
    void entraComASenhaCertaEIgnoraMaiusculasNoEmail() throws Exception {
        cadastrarAna();
        entrar("ANA@teste.dev", SENHA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.email").value(EMAIL))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void senhaErradaContaInexistenteEContaSemSenhaTemAMesmaResposta() throws Exception {
        cadastrarAna();
        String senhaErrada = ler(entrar(EMAIL, "senha-errada-9").andExpect(status().isUnauthorized()), "$.mensagem");
        String semConta = ler(entrar("ninguem@teste.dev", SENHA).andExpect(status().isUnauthorized()), "$.mensagem");

        cadastrar("bruno@teste.dev", "ALUNO", "Bruno", null).andExpect(status().isCreated());
        jdbc.update("update usuario set senha_hash = null where email = 'bruno@teste.dev'");
        String semSenha = ler(entrar("bruno@teste.dev", SENHA).andExpect(status().isUnauthorized()), "$.mensagem");

        assertThat(semConta).isEqualTo(senhaErrada);
        assertThat(semSenha).isEqualTo(senhaErrada);
        entrar(EMAIL, "senha-errada-9").andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));
    }

    @Test
    void cincoSenhasErradasBloqueiamOLoginPorUmTempo() throws Exception {
        cadastrarAna();
        for (int i = 0; i < 4; i++) {
            entrar(EMAIL, "senha-errada-9").andExpect(status().isUnauthorized());
        }
        entrar(EMAIL, "senha-errada-9")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("MUITAS_TENTATIVAS"));
        entrar(EMAIL, SENHA).andExpect(status().isTooManyRequests());

        jdbc.update("update usuario set bloqueado_ate = now() - interval '1 second'");
        entrar(EMAIL, SENHA).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select falhas_login from usuario", Integer.class)).isZero();
    }

    @Test
    void acertoZeraAsFalhasAntesDoBloqueio() throws Exception {
        cadastrarAna();
        for (int i = 0; i < 4; i++) {
            entrar(EMAIL, "senha-errada-9").andExpect(status().isUnauthorized());
        }
        entrar(EMAIL, SENHA).andExpect(status().isOk());
        entrar(EMAIL, "senha-errada-9").andExpect(status().isUnauthorized());
        entrar(EMAIL, SENHA).andExpect(status().isOk());
    }

    // ======================== esqueci a senha ========================

    @Test
    void redefinirSenhaEncerraAsSessoesETrocaASenha() throws Exception {
        String refreshAntigo = ler(cadastrarAna(), "$.refreshToken");

        pedirCodigo(EMAIL);
        redefinir(EMAIL, CODIGO, NOVA_SENHA).andExpect(status().isNoContent());

        renovar(refreshAntigo).andExpect(status().isUnauthorized());
        entrar(EMAIL, SENHA).andExpect(status().isUnauthorized());
        entrar(EMAIL, NOVA_SENHA).andExpect(status().isOk());
        redefinir(EMAIL, CODIGO, "terceira-senha-3")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
    }

    @Test
    void redefinirTambemDesbloqueiaOLogin() throws Exception {
        cadastrarAna();
        for (int i = 0; i < 5; i++) {
            entrar(EMAIL, "senha-errada-9");
        }
        entrar(EMAIL, SENHA).andExpect(status().isTooManyRequests());
        pedirCodigo(EMAIL);
        redefinir(EMAIL, CODIGO, NOVA_SENHA).andExpect(status().isNoContent());
        entrar(EMAIL, NOVA_SENHA).andExpect(status().isOk());
    }

    @Test
    void contaSemSenhaCriaUmaPeloEsqueciASenha() throws Exception {
        cadastrarAna();
        jdbc.update("update usuario set senha_hash = null");
        pedirCodigo(EMAIL);
        redefinir(EMAIL, CODIGO, NOVA_SENHA).andExpect(status().isNoContent());
        entrar(EMAIL, NOVA_SENHA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.temSenha").value(true));
    }

    @Test
    void emailSemContaRespondeIgualENaoRedefineNada() throws Exception {
        pedirCodigo("ninguem@teste.dev");
        redefinir("ninguem@teste.dev", CODIGO, NOVA_SENHA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
        assertThat(jdbc.queryForObject("select count(*) from usuario", Integer.class)).isZero();
    }

    @Test
    void senhaFracaNaRedefinicaoNaoGastaOCodigo() throws Exception {
        cadastrarAna();
        pedirCodigo(EMAIL);
        redefinir(EMAIL, CODIGO, "somenteletras")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SENHA_FRACA"));
        redefinir(EMAIL, CODIGO, NOVA_SENHA).andExpect(status().isNoContent());
    }

    @Test
    void codigoErradoERecusadoEQueimaAposCincoTentativas() throws Exception {
        cadastrarAna();
        pedirCodigo(EMAIL);
        for (int i = 0; i < 5; i++) {
            redefinir(EMAIL, "000000", NOVA_SENHA).andExpect(status().isUnauthorized());
        }
        redefinir(EMAIL, CODIGO, NOVA_SENHA).andExpect(status().isUnauthorized());
        entrar(EMAIL, SENHA).andExpect(status().isOk());
    }

    @Test
    void limitaPedidosDeCodigoPorEmailComOuSemConta() throws Exception {
        cadastrarAna();
        for (String email : new String[] {EMAIL, "ninguem@teste.dev"}) {
            pedirCodigo(email);
            pedirCodigo(email);
            pedirCodigo(email);
            post("/api/v1/auth/senha/codigo", null, "{\"email\":\"%s\"}".formatted(email))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.codigo").value("MUITAS_SOLICITACOES"));
        }
    }

    // ======================== troca de senha logado ========================

    @Test
    void trocarSenhaLogadoExigeOCodigoPorEmailEDevolveUmaSessaoNova() throws Exception {
        ResultActions sessao = cadastrarAna();
        String token = ler(sessao, "$.accessToken");
        String refreshAntigo = ler(sessao, "$.refreshToken");

        // 422 e não 401: o cliente trataria 401 como sessão expirada
        trocarSenha(token, CODIGO, NOVA_SENHA)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));

        pedirCodigoTroca(token);
        trocarSenha(token, "000000", NOVA_SENHA)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
        trocarSenha(token, CODIGO, "somenteletras")
                .andExpect(jsonPath("$.codigo").value("SENHA_FRACA"));

        ResultActions nova = trocarSenha(token, CODIGO, NOVA_SENHA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        renovar(refreshAntigo).andExpect(status().isUnauthorized());
        renovar(ler(nova, "$.refreshToken")).andExpect(status().isOk());
        entrar(EMAIL, SENHA).andExpect(status().isUnauthorized());
        entrar(EMAIL, NOVA_SENHA).andExpect(status().isOk());
        trocarSenha(ler(nova, "$.accessToken"), CODIGO, "terceira-senha-3")
                .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
    }

    @Test
    void codigoDeTrocaQueimaAposCincoErros() throws Exception {
        String token = ler(cadastrarAna(), "$.accessToken");
        pedirCodigoTroca(token);
        for (int i = 0; i < 5; i++) {
            trocarSenha(token, "000000", NOVA_SENHA).andExpect(status().isUnprocessableEntity());
        }
        trocarSenha(token, CODIGO, NOVA_SENHA).andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
        entrar(EMAIL, SENHA).andExpect(status().isOk());
    }

    @Test
    void contaSemSenhaCriaASenhaPeloCodigo() throws Exception {
        String token = ler(cadastrarAna(), "$.accessToken");
        jdbc.update("update usuario set senha_hash = null");
        get("/api/v1/eu", token).andExpect(jsonPath("$.temSenha").value(false));

        pedirCodigoTroca(token);
        trocarSenha(token, CODIGO, NOVA_SENHA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.temSenha").value(true));
        entrar(EMAIL, NOVA_SENHA).andExpect(status().isOk());
    }

    // ======================== sessão ========================

    @Test
    void refreshTokenGiraENaoPodeSerReutilizado() throws Exception {
        String refresh = ler(cadastrarAna(), "$.refreshToken");
        renovar(refresh).andExpect(status().isOk());
        renovar(refresh).andExpect(status().isUnauthorized());
    }

    @Test
    void rotasExigemTokenEPapelCorreto() throws Exception {
        get("/api/v1/eu", null).andExpect(status().isUnauthorized());
        get("/api/v1/eu", "token-invalido").andExpect(status().isUnauthorized());
        put("/api/v1/eu/senha", null, "{\"codigo\":\"%s\",\"novaSenha\":\"%s\"}".formatted(CODIGO, NOVA_SENHA))
                .andExpect(status().isUnauthorized());
        post("/api/v1/eu/senha/codigo", null, null).andExpect(status().isUnauthorized());
        get("/api/v1/professor/turmas", aluno("Ana")).andExpect(status().isForbidden());
        get("/api/v1/aluno/atividades", professor()).andExpect(status().isForbidden());
    }
}
