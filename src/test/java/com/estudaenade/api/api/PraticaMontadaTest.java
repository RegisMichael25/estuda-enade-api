package com.estudaenade.api.api;

import com.estudaenade.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prova montada pelo próprio aluno na prática livre: contagem por tema, filtros múltiplos e sorteio. */
class PraticaMontadaTest extends IntegrationTest {

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

    private String questao(String tema, int ano) throws Exception {
        String id = questaoPublicada(professor, curso, tema, 0);
        jdbc.update("update questao set ano = ? where id = ?::uuid", ano, id);
        return id;
    }

    private String sortear(String corpoExtra) {
        return "{\"cursoId\":\"%s\"%s}".formatted(curso, corpoExtra);
    }

    @Test
    void contaQuestoesPorTemaRespeitandoOsAnos() throws Exception {
        questao(bancoDeDados, 2021);
        questao(bancoDeDados, 2023);
        questao(redes, 2023);
        criarQuestao(professor, curso, redes, 0); // rascunho não conta

        get("/api/v1/aluno/pratica/contagem?cursoId=" + curso, ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].tema.nome").value("Banco de Dados"))
                .andExpect(jsonPath("$[0].total").value(2))
                .andExpect(jsonPath("$[1].tema.nome").value("Redes"))
                .andExpect(jsonPath("$[1].total").value(1));

        get("/api/v1/aluno/pratica/contagem?cursoId=" + curso + "&ano=2021", ana)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tema.id").value(bancoDeDados));
    }

    @Test
    void listagemAceitaVariosTemasEAnos() throws Exception {
        questao(bancoDeDados, 2021);
        questao(redes, 2022);
        questao(redes, 2023);

        get("/api/v1/aluno/pratica/questoes?cursoId=%s&temaId=%s&temaId=%s".formatted(curso, bancoDeDados, redes), ana)
                .andExpect(jsonPath("$.total").value(3));
        get("/api/v1/aluno/pratica/questoes?cursoId=%s&ano=2021&ano=2023".formatted(curso), ana)
                .andExpect(jsonPath("$.total").value(2));
        get("/api/v1/aluno/pratica/questoes?cursoId=%s&temaId=%s&ano=2021".formatted(curso, redes), ana)
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void sorteioRespeitaFiltrosNaoRepeteENaoEntregaGabarito() throws Exception {
        Set<String> deRedes = new HashSet<>();
        for (int i = 0; i < 4; i++) deRedes.add(questao(redes, 2023));
        questao(bancoDeDados, 2023);
        String jaEscolhida = deRedes.iterator().next();

        List<String> ids = ler(post("/api/v1/aluno/pratica/sortear", ana,
                        sortear(",\"temaIds\":[\"%s\"],\"quantidade\":10,\"excluirIds\":[\"%s\"]".formatted(redes, jaEscolhida)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$", hasSize(3)))
                        .andExpect(jsonPath("$[0].alternativas[0].correta").doesNotExist())
                        .andExpect(jsonPath("$[0].explicacao").doesNotExist()),
                "$[*].id");

        assertEquals(3, new HashSet<>(ids).size(), "o sorteio não repete questões");
        assertTrue(deRedes.containsAll(ids), "só questões do tema escolhido");
        assertFalse(ids.contains(jaEscolhida), "não traz a que já está na prova");

        post("/api/v1/aluno/pratica/sortear", ana, sortear(",\"quantidade\":2"))
                .andExpect(jsonPath("$", hasSize(2)));
        post("/api/v1/aluno/pratica/sortear", ana, sortear(",\"anos\":[2021],\"quantidade\":5"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void sorteioValidaQuantidadeECurso() throws Exception {
        post("/api/v1/aluno/pratica/sortear", ana, sortear(",\"quantidade\":0"))
                .andExpect(status().isBadRequest());
        post("/api/v1/aluno/pratica/sortear", ana, sortear(",\"quantidade\":101"))
                .andExpect(status().isBadRequest());
        post("/api/v1/aluno/pratica/sortear", ana, "{\"quantidade\":5}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void professorNaoUsaAsRotasDaPraticaDoAluno() throws Exception {
        get("/api/v1/aluno/pratica/contagem?cursoId=" + curso, professor).andExpect(status().isForbidden());
        post("/api/v1/aluno/pratica/sortear", professor, sortear(",\"quantidade\":5")).andExpect(status().isForbidden());
    }

    /** Responde pela prática livre marcando a alternativa de índice {@code i} (0 = A). */
    private void responderNaPratica(String questao, int i) throws Exception {
        String alternativa = jdbc.queryForObject(
                "select id::text from alternativa where questao_id = ?::uuid order by ordem offset ? limit 1",
                String.class, questao, i);
        post("/api/v1/aluno/pratica/questoes/" + questao + "/responder", ana,
                "{\"alternativaId\":\"%s\"}".formatted(alternativa)).andExpect(status().isOk());
    }

    @Test
    void filtraPeloHistoricoDoAluno() throws Exception {
        String acertada = questao(redes, 2023);   // correta: A
        String errada = questao(redes, 2023);
        String nova = questao(redes, 2023);
        responderNaPratica(acertada, 0);
        responderNaPratica(errada, 1);

        String base = "/api/v1/aluno/pratica/questoes?cursoId=" + curso;
        get(base + "&historico=TODAS", ana).andExpect(jsonPath("$.total").value(3));
        get(base + "&historico=NAO_RESPONDIDAS", ana)
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.itens[0].id").value(nova));
        get(base + "&historico=ERRADAS", ana)
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.itens[0].id").value(errada));
        post("/api/v1/aluno/pratica/sortear", ana, sortear(",\"quantidade\":10,\"historico\":\"ERRADAS\""))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(errada));

        // a contagem respeita o filtro e traz o desempenho do aluno no tema
        get("/api/v1/aluno/pratica/contagem?historico=ERRADAS&cursoId=" + curso, ana)
                .andExpect(jsonPath("$[0].total").value(1))
                .andExpect(jsonPath("$[0].respondidas").value(2))
                .andExpect(jsonPath("$[0].acertos").value(1));

        // acertou depois: a última resposta conta, e ela sai de "Só as que errei"
        responderNaPratica(errada, 0);
        get(base + "&historico=ERRADAS", ana).andExpect(jsonPath("$.total").value(0));
        get("/api/v1/aluno/pratica/contagem?historico=ERRADAS&cursoId=" + curso, ana).andExpect(jsonPath("$", hasSize(0)));

        // o histórico é de cada aluno
        get(base + "&historico=NAO_RESPONDIDAS", aluno("Bruno")).andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void sorteioEquilibradoRepartePorTema() throws Exception {
        for (int i = 0; i < 4; i++) questao(redes, 2023);
        Set<String> deBanco = new HashSet<>(List.of(questao(bancoDeDados, 2023), questao(bancoDeDados, 2023)));

        for (int rodada = 0; rodada < 5; rodada++) {
            List<String> ids = ler(post("/api/v1/aluno/pratica/sortear", ana, sortear(",\"quantidade\":4,\"equilibrar\":true"))
                    .andExpect(jsonPath("$", hasSize(4))), "$[*].id");
            assertEquals(2, ids.stream().filter(deBanco::contains).count(), "duas de cada tema");
        }
        // sem questões suficientes em um tema, completa com o outro
        post("/api/v1/aluno/pratica/sortear", ana, sortear(",\"quantidade\":6,\"equilibrar\":true"))
                .andExpect(jsonPath("$", hasSize(6)));
    }
}
