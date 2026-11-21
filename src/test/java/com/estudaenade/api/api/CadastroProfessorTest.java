package com.estudaenade.api.api;

import com.estudaenade.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quem pode atuar como professor: convite, domínio institucional ou aprovação de outro professor. */
class CadastroProfessorTest extends IntegrationTest {

    /** E-mail fora do domínio institucional dos testes (teste.dev). */
    private static final String EMAIL_COMUM = "carla@email-comum.test";

    @Test
    void professorDeEmailComumFicaPendenteAteOutroProfessorAprovar() throws Exception {
        String aprovador = professor();

        ResultActions sessao = cadastrar(EMAIL_COMUM, "PROFESSOR", "Carla", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.papel").value("PROFESSOR"))
                .andExpect(jsonPath("$.usuario.aprovado").value(false));
        String pendente = ler(sessao, "$.accessToken");
        String refresh = ler(sessao, "$.refreshToken");
        String carlaId = ler(sessao, "$.usuario.id");

        // pendente não chega ao banco de questões, às turmas nem à própria aprovação
        get("/api/v1/professor/questoes", pendente)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CADASTRO_PENDENTE"));
        get("/api/v1/professor/turmas", pendente).andExpect(status().isForbidden());
        post("/api/v1/professor/turmas", pendente, "{\"nome\":\"Turma\"}").andExpect(status().isForbidden());
        post("/api/v1/professor/convites", pendente, null).andExpect(status().isForbidden());
        post("/api/v1/professor/professores/" + carlaId + "/aprovar", pendente, null).andExpect(status().isForbidden());
        get("/api/v1/aluno/pratica/questoes", pendente).andExpect(status().isForbidden());
        // a própria conta ele enxerga, para saber que está aguardando
        get("/api/v1/eu", pendente).andExpect(status().isOk()).andExpect(jsonPath("$.aprovado").value(false));

        get("/api/v1/professor/professores/pendentes", aprovador)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value(EMAIL_COMUM));
        post("/api/v1/professor/professores/" + carlaId + "/aprovar", aprovador, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprovado").value(true));
        post("/api/v1/professor/professores/" + carlaId + "/aprovar", aprovador, null).andExpect(status().isOk()); // repetir não gera erro
        get("/api/v1/professor/professores/pendentes", aprovador).andExpect(jsonPath("$", hasSize(0)));

        // o token antigo continua pendente; a sessão renovada já tem acesso
        get("/api/v1/professor/turmas", pendente).andExpect(status().isForbidden());
        ResultActions renovada = post("/api/v1/auth/renovar", null, "{\"refreshToken\":\"%s\"}".formatted(refresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.aprovado").value(true));
        get("/api/v1/professor/turmas", ler(renovada, "$.accessToken")).andExpect(status().isOk());
    }

    @Test
    void conviteAprovaNaHoraESoValeUmaVez() throws Exception {
        String anfitriao = professor();
        ResultActions criado = post("/api/v1/professor/convites", anfitriao, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").isNotEmpty());
        String convite = ler(criado, "$.codigo");

        // aceita minúsculas e sem hífen
        ResultActions sessao = cadastrar(EMAIL_COMUM, "PROFESSOR", "Carla", convite.replace("-", "").toLowerCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.aprovado").value(true));
        get("/api/v1/professor/questoes", ler(sessao, "$.accessToken")).andExpect(status().isOk());

        get("/api/v1/professor/convites", anfitriao)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].usado").value(true))
                .andExpect(jsonPath("$[0].usadoPor").value("Carla"))
                .andExpect(jsonPath("$[0].codigo").doesNotExist());

        // o mesmo convite não aprova uma segunda pessoa
        String outro = "diego@email-comum.test";
        cadastrar(outro, "PROFESSOR", "Diego", convite)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONVITE_INVALIDO"));
        // a conta não foi criada e o código de acesso não foi gasto: sem convite, ele entra pendente
        entrar(outro, "PROFESSOR", "Diego", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.aprovado").value(false));
    }

    @Test
    void conviteInexistenteOuExpiradoERecusado() throws Exception {
        String anfitriao = professor();
        cadastrar(EMAIL_COMUM, "PROFESSOR", "Carla", "AAAAA-AAAAA")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONVITE_INVALIDO"));

        String convite = ler(post("/api/v1/professor/convites", anfitriao, null), "$.codigo");
        jdbc.update("update convite_professor set expira_em = now() - interval '1 minute'");
        entrar(EMAIL_COMUM, "PROFESSOR", "Carla", convite)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONVITE_INVALIDO"));
    }

    @Test
    void emailInstitucionalEntraAprovadoMasDominioParecidoNao() throws Exception {
        cadastrar("bia@teste.dev", "PROFESSOR", "Bia", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.aprovado").value(true));
        cadastrar("bia@falso-teste.dev", "PROFESSOR", "Bia", null)
                .andExpect(jsonPath("$.usuario.aprovado").value(false));
        cadastrar("bia@teste.dev.exemplo.test", "PROFESSOR", "Bia", null)
                .andExpect(jsonPath("$.usuario.aprovado").value(false));
    }

    @Test
    void alunoNaoPrecisaDeAprovacaoENaoAprovaProfessor() throws Exception {
        ResultActions sessao = cadastrar("ana@email-comum.test", "ALUNO", "Ana", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.aprovado").value(true));
        String ana = ler(sessao, "$.accessToken");
        get("/api/v1/aluno/atividades", ana).andExpect(status().isOk());

        String carlaId = ler(cadastrar(EMAIL_COMUM, "PROFESSOR", "Carla", null), "$.usuario.id");
        post("/api/v1/professor/professores/" + carlaId + "/aprovar", ana, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
        // aluno não pode ser "aprovado" como professor
        post("/api/v1/professor/professores/" + ler(sessao, "$.usuario.id") + "/aprovar", professor(), null)
                .andExpect(status().isNotFound());
    }
}
