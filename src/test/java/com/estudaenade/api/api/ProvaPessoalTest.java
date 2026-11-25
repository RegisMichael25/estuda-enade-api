package com.estudaenade.api.api;

import com.estudaenade.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prova montada pelo aluno e salva no servidor: retomar, concluir, refazer e isolamento. */
class ProvaPessoalTest extends IntegrationTest {

    private String professor;
    private String curso;
    private String bancoDeDados;
    private String redes;
    private String ana;

    @BeforeEach
    void preparar() throws Exception {
        professor = professor();
        curso = criarCurso(professor, "ADS");
        bancoDeDados = criarTema(professor, curso, "Banco de Dados");
        redes = criarTema(professor, curso, "Redes");
        ana = aluno("Ana");
    }

    private ResultActions criarProva(String aluno, String nome, String... questaoIds) throws Exception {
        String ids = "\"" + String.join("\",\"", questaoIds) + "\"";
        String campoNome = nome == null ? "" : ",\"nome\":\"" + nome + "\"";
        return post("/api/v1/aluno/provas", aluno, "{\"questaoIds\":[%s]%s}".formatted(ids, campoNome));
    }

    private ResultActions responder(String aluno, String prova, String questao, String alternativa) throws Exception {
        return post("/api/v1/aluno/provas/%s/questoes/%s/responder".formatted(prova, questao), aluno,
                "{\"alternativaId\":\"%s\"}".formatted(alternativa));
    }

    /** Alternativa de índice {@code i} (0 = A) da questão na posição {@code ordem} da prova. */
    private static String alternativa(ResultActions prova, int ordem, int i) throws Exception {
        return ler(prova, "$.questoes[%d].questao.alternativas[%d].id".formatted(ordem, i));
    }

    @Test
    void salvaRetomaComRespostasEConclui() throws Exception {
        String q1 = questaoPublicada(professor, curso, bancoDeDados, 2);
        String q2 = questaoPublicada(professor, curso, redes, 0);

        ResultActions criada = criarProva(ana, null, q1, q2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prova.nome").value("Banco de Dados e Redes"))
                .andExpect(jsonPath("$.prova.total").value(2))
                .andExpect(jsonPath("$.prova.concluidaEm").doesNotExist())
                .andExpect(jsonPath("$.questoes[0].questao.id").value(q1))
                .andExpect(jsonPath("$.questoes[0].disponivel").value(true))
                .andExpect(jsonPath("$.questoes[0].gabarito").doesNotExist())
                .andExpect(jsonPath("$.questoes[0].questao.alternativas[0].correta").doesNotExist());
        String prova = ler(criada, "$.prova.id");

        responder(ana, prova, q1, alternativa(criada, 0, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acertou").value(true))
                .andExpect(jsonPath("$.letraCorreta").value("C"));
        responder(ana, prova, q1, alternativa(criada, 0, 1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("QUESTAO_JA_RESPONDIDA"));

        // retomando (outro aparelho): a respondida volta com gabarito, a outra sem
        get("/api/v1/aluno/provas/" + prova, ana)
                .andExpect(jsonPath("$.prova.respondidas").value(1))
                .andExpect(jsonPath("$.prova.acertos").value(1))
                .andExpect(jsonPath("$.questoes[0].gabarito.acertou").value(true))
                .andExpect(jsonPath("$.questoes[0].gabarito.alternativaMarcadaId").value(alternativa(criada, 0, 2)))
                .andExpect(jsonPath("$.questoes[1].gabarito").doesNotExist());

        get("/api/v1/aluno/provas", ana)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].temas", hasSize(2)))
                .andExpect(jsonPath("$[0].respondidas").value(1));

        post("/api/v1/aluno/provas/" + prova + "/concluir", ana, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prova.concluidaEm").isNotEmpty());
        responder(ana, prova, q2, alternativa(criada, 1, 0))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PROVA_CONCLUIDA"));

        // a resposta também conta no desempenho do próprio aluno
        get("/api/v1/aluno/desempenho", ana).andExpect(jsonPath("$.respondidas").value(1));
    }

    @Test
    void refazerEPraticarErradas() throws Exception {
        String certa = questaoPublicada(professor, curso, redes, 0);
        String errada = questaoPublicada(professor, curso, redes, 0);
        String pulada = questaoPublicada(professor, curso, redes, 0);
        ResultActions criada = criarProva(ana, "Revisão de redes", certa, errada, pulada)
                .andExpect(jsonPath("$.prova.nome").value("Revisão de redes"));
        String prova = ler(criada, "$.prova.id");
        responder(ana, prova, certa, alternativa(criada, 0, 0)).andExpect(jsonPath("$.acertou").value(true));
        responder(ana, prova, errada, alternativa(criada, 1, 3)).andExpect(jsonPath("$.acertou").value(false));

        post("/api/v1/aluno/provas/" + prova + "/erradas", ana, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prova.nome").value("Erradas · Revisão de redes"))
                .andExpect(jsonPath("$.questoes", hasSize(2)))
                .andExpect(jsonPath("$.questoes[0].questao.id").value(errada))
                .andExpect(jsonPath("$.questoes[1].questao.id").value(pulada));

        post("/api/v1/aluno/provas/" + prova + "/refazer", ana, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.questoes", hasSize(3)))
                .andExpect(jsonPath("$.prova.respondidas").value(0))
                .andExpect(jsonPath("$.questoes[0].gabarito").doesNotExist());

        get("/api/v1/aluno/provas", ana).andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void questaoOcultaDepoisFicaIndisponivelESaiDoRefazer() throws Exception {
        String q1 = questaoPublicada(professor, curso, redes, 0);
        String q2 = questaoPublicada(professor, curso, redes, 0);
        ResultActions criada = criarProva(ana, null, q1, q2);
        String prova = ler(criada, "$.prova.id");
        responder(ana, prova, q1, alternativa(criada, 0, 0)).andExpect(status().isOk());

        post("/api/v1/professor/questoes/" + q1 + "/ocultar", professor, null).andExpect(status().isOk());

        get("/api/v1/aluno/provas/" + prova, ana)
                .andExpect(jsonPath("$.questoes[0].disponivel").value(false))
                .andExpect(jsonPath("$.questoes[0].gabarito").doesNotExist())
                .andExpect(jsonPath("$.questoes[1].disponivel").value(true));
        post("/api/v1/aluno/provas/" + prova + "/refazer", ana, null)
                .andExpect(jsonPath("$.questoes", hasSize(1)))
                .andExpect(jsonPath("$.questoes[0].questao.id").value(q2));
    }

    @Test
    void naoCriaComQuestaoIndisponivelDeOutroCursoOuVazia() throws Exception {
        String publicada = questaoPublicada(professor, curso, redes, 0);
        String rascunho = ler(criarQuestao(professor, curso, redes, 0), "$.id");
        String outroCurso = criarCurso(professor, "Engenharia");
        String deOutroCurso = questaoPublicada(professor, outroCurso, criarTema(professor, outroCurso, "Cálculo"), 0);

        criarProva(ana, null, publicada, rascunho)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("QUESTAO_INDISPONIVEL"));
        criarProva(ana, null, publicada, deOutroCurso)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CURSOS_DIFERENTES"));
        post("/api/v1/aluno/provas", ana, "{\"questaoIds\":[]}").andExpect(status().isBadRequest());
        get("/api/v1/aluno/provas", ana).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void provaEhSoDoAlunoQueAMontou() throws Exception {
        String q = questaoPublicada(professor, curso, redes, 0);
        ResultActions criada = criarProva(ana, null, q);
        String prova = ler(criada, "$.prova.id");
        String bruno = aluno("Bruno");

        get("/api/v1/aluno/provas/" + prova, bruno).andExpect(status().isNotFound());
        responder(bruno, prova, q, alternativa(criada, 0, 0)).andExpect(status().isNotFound());
        post("/api/v1/aluno/provas/" + prova + "/refazer", bruno, null).andExpect(status().isNotFound());
        delete("/api/v1/aluno/provas/" + prova, bruno).andExpect(status().isNotFound());
        get("/api/v1/aluno/provas", bruno).andExpect(jsonPath("$", hasSize(0)));

        get("/api/v1/aluno/provas", professor).andExpect(status().isForbidden());
        get("/api/v1/aluno/provas/" + prova, professor).andExpect(status().isForbidden());
    }

    @Test
    void excluirTiraDoHistoricoMasMantemODesempenho() throws Exception {
        String q = questaoPublicada(professor, curso, redes, 0);
        ResultActions criada = criarProva(ana, null, q);
        String prova = ler(criada, "$.prova.id");
        responder(ana, prova, q, alternativa(criada, 0, 0)).andExpect(status().isOk());

        delete("/api/v1/aluno/provas/" + prova, ana).andExpect(status().isNoContent());
        get("/api/v1/aluno/provas", ana).andExpect(jsonPath("$", hasSize(0)));
        get("/api/v1/aluno/provas/" + prova, ana).andExpect(status().isNotFound());
        get("/api/v1/aluno/desempenho", ana).andExpect(jsonPath("$.respondidas").value(1));
    }
}
