package com.estudaenade.api.api;

import com.estudaenade.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ComentarioAtividadeTest extends IntegrationTest {

    private String prof;
    private String ana;
    private String bruno;
    private String turmaId;
    private String codigoConvite;
    private String atividade;

    @BeforeEach
    void preparar() throws Exception {
        prof = professor();
        ana = aluno("Ana Souza");
        bruno = aluno("Bruno Lima");
        ResultActions turma = criarTurma(prof);
        turmaId = ler(turma, "$.id");
        codigoConvite = ler(turma, "$.codigoConvite");
        String curso = criarCurso(prof, "ADS");
        String questao = questaoPublicada(prof, curso, criarTema(prof, curso, "Redes"), 1);

        atividade = ler(post("/api/v1/professor/turmas/" + turmaId + "/atividades", prof, """
                {"titulo":"Simulado 1","tipo":"SIMULADO","duracao":null,"maxTentativas":2,"questaoIds":["%s"]}
                """.formatted(questao)).andExpect(status().isCreated()), "$.id");
        post("/api/v1/professor/atividades/" + atividade + "/publicar", prof, null).andExpect(status().isOk());

        entrarNaTurma(ana);
        entrarNaTurma(bruno);
    }

    private void entrarNaTurma(String aluno) throws Exception {
        post("/api/v1/aluno/turmas/entrar", aluno, "{\"codigo\":\"%s\"}".formatted(codigoConvite)).andExpect(status().isOk());
    }

    private String iniciarTentativa(String aluno) throws Exception {
        return ler(post("/api/v1/aluno/atividades/" + atividade + "/tentativas", aluno, null), "$.id");
    }

    private void enviarTentativa(String aluno) throws Exception {
        post("/api/v1/aluno/tentativas/" + iniciarTentativa(aluno) + "/enviar", aluno, null).andExpect(status().isOk());
    }

    private ResultActions comentar(String rota, String token, String texto) throws Exception {
        return post(rota + "/atividades/" + atividade + "/comentarios", token, "{\"texto\":\"%s\"}".formatted(texto));
    }

    @Test
    void alunoSoVeEComentaDepoisDeEnviarATentativa() throws Exception {
        String muralAluno = "/api/v1/aluno/atividades/" + atividade + "/comentarios";

        get(muralAluno, ana).andExpect(status().isNotFound());
        comentar("/api/v1/aluno", ana, "Antes").andExpect(status().isNotFound());

        iniciarTentativa(ana); // em andamento ainda não libera
        get(muralAluno, ana).andExpect(status().isNotFound());

        enviarTentativa(ana);
        get(muralAluno, ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeComentar").value(true))
                .andExpect(jsonPath("$.comentarios", hasSize(0)));
        comentar("/api/v1/aluno", ana, "A questão 1 pegou todo mundo?")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autor.nome").value("Ana Souza"))
                .andExpect(jsonPath("$.papelAutor").value("ALUNO"))
                .andExpect(jsonPath("$.meu").value(true));
        comentar("/api/v1/professor", prof, "Revisem a camada de transporte.").andExpect(status().isCreated());

        // aberto à turma: o professor e quem já enviou veem tudo, em ordem de envio
        get("/api/v1/professor/atividades/" + atividade + "/comentarios", prof)
                .andExpect(jsonPath("$.comentarios", hasSize(2)))
                .andExpect(jsonPath("$.comentarios[0].papelAutor").value("ALUNO"))
                .andExpect(jsonPath("$.comentarios[1].papelAutor").value("PROFESSOR"));

        // Bruno ainda não enviou; alunos de fora da turma nunca veem
        get(muralAluno, bruno).andExpect(status().isNotFound());
        get(muralAluno, aluno("Carla")).andExpect(status().isNotFound());
        enviarTentativa(bruno);
        get(muralAluno, bruno)
                .andExpect(jsonPath("$.comentarios", hasSize(2)))
                .andExpect(jsonPath("$.comentarios[0].meu").value(false))
                .andExpect(jsonPath("$.comentarios[0].podeEditar").value(false))
                .andExpect(jsonPath("$.comentarios[0].podeApagar").value(false));
    }

    @Test
    void professorDeOutraTurmaNaoVeOMural() throws Exception {
        String outro = entrarComo("PROFESSOR", "Prof. Lima");
        get("/api/v1/professor/atividades/" + atividade + "/comentarios", outro).andExpect(status().isNotFound());
        comentar("/api/v1/professor", outro, "Oi").andExpect(status().isNotFound());
    }

    @Test
    void autorEditaEApagaEProfessorApagaQualquerUm() throws Exception {
        enviarTentativa(ana);
        enviarTentativa(bruno);
        String daAna = ler(comentar("/api/v1/aluno", ana, "Original").andExpect(status().isCreated()), "$.id");

        put("/api/v1/aluno/comentarios/" + daAna, ana, "{\"texto\":\"Corrigido\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("Corrigido"))
                .andExpect(jsonPath("$.editadoEm", notNullValue()));

        // colega não edita nem apaga; professor apaga, mas não edita
        put("/api/v1/aluno/comentarios/" + daAna, bruno, "{\"texto\":\"x\"}").andExpect(status().isForbidden());
        delete("/api/v1/aluno/comentarios/" + daAna, bruno).andExpect(status().isForbidden());
        put("/api/v1/professor/comentarios/" + daAna, prof, "{\"texto\":\"x\"}").andExpect(status().isForbidden());
        get("/api/v1/professor/atividades/" + atividade + "/comentarios", prof)
                .andExpect(jsonPath("$.comentarios[0].podeEditar").value(false))
                .andExpect(jsonPath("$.comentarios[0].podeApagar").value(true));
        delete("/api/v1/professor/comentarios/" + daAna, prof).andExpect(status().isNoContent());

        String doBruno = ler(comentar("/api/v1/aluno", bruno, "Meu").andExpect(status().isCreated()), "$.id");
        delete("/api/v1/aluno/comentarios/" + doBruno, bruno).andExpect(status().isNoContent());
        get("/api/v1/aluno/atividades/" + atividade + "/comentarios", ana).andExpect(jsonPath("$.comentarios", hasSize(0)));
    }

    @Test
    void turmaArquivadaDeixaOMuralSoParaLeitura() throws Exception {
        enviarTentativa(ana);
        String id = ler(comentar("/api/v1/aluno", ana, "Antes de arquivar").andExpect(status().isCreated()), "$.id");
        post("/api/v1/professor/turmas/" + turmaId + "/arquivar", prof, null).andExpect(status().isOk());

        get("/api/v1/aluno/atividades/" + atividade + "/comentarios", ana)
                .andExpect(jsonPath("$.podeComentar").value(false))
                .andExpect(jsonPath("$.comentarios[0].podeEditar").value(false))
                .andExpect(jsonPath("$.comentarios[0].editadoEm", nullValue()));
        comentar("/api/v1/aluno", ana, "Depois")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TURMA_ARQUIVADA"));
        put("/api/v1/aluno/comentarios/" + id, ana, "{\"texto\":\"x\"}")
                .andExpect(jsonPath("$.codigo").value("TURMA_ARQUIVADA"));
        delete("/api/v1/aluno/comentarios/" + id, ana).andExpect(status().isNoContent());
    }

    @Test
    void rascunhoNaoRecebeComentarioEAlunoNaoVe() throws Exception {
        String rascunho = ler(post("/api/v1/professor/turmas/" + turmaId + "/atividades", prof, """
                {"titulo":"Rascunho","tipo":"LISTA","duracao":null,"maxTentativas":null,"questaoIds":[]}
                """).andExpect(status().isCreated()), "$.id");

        get("/api/v1/professor/atividades/" + rascunho + "/comentarios", prof)
                .andExpect(jsonPath("$.podeComentar").value(false));
        post("/api/v1/professor/atividades/" + rascunho + "/comentarios", prof, "{\"texto\":\"Oi\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ATIVIDADE_NAO_PUBLICADA"));
        get("/api/v1/aluno/atividades/" + rascunho + "/comentarios", ana).andExpect(status().isNotFound());
    }

    @Test
    void comentarioPrecisaDeTexto() throws Exception {
        enviarTentativa(ana);
        comentar("/api/v1/aluno", ana, "   ").andExpect(status().isBadRequest());
        comentar("/api/v1/aluno", ana, "x".repeat(2001)).andExpect(status().isBadRequest());
    }
}
