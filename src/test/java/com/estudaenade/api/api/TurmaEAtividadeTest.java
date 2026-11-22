package com.estudaenade.api.api;

import com.estudaenade.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TurmaEAtividadeTest extends IntegrationTest {

    private String prof;
    private String ana;
    private String turmaId;
    private String codigoConvite;
    private String q1;
    private String q2;

    @BeforeEach
    void preparar() throws Exception {
        prof = professor();
        ana = aluno("Ana Souza");
        ResultActions turma = criarTurma(prof);
        turmaId = ler(turma, "$.id");
        codigoConvite = ler(turma, "$.codigoConvite");
        String curso = criarCurso(prof, "ADS");
        q1 = questaoPublicada(prof, curso, criarTema(prof, "Banco de Dados"), 2);
        q2 = questaoPublicada(prof, curso, criarTema(prof, "Redes"), 1);
    }

    /** Cria e publica um simulado com q1 e q2; devolve o id. */
    private String atividadePublicada(String duracao, String maxTentativas) throws Exception {
        return atividadePublicada("SIMULADO", duracao, maxTentativas);
    }

    /** Cria e publica uma atividade do tipo informado com q1 e q2; devolve o id. */
    private String atividadePublicada(String tipo, String duracao, String maxTentativas) throws Exception {
        String id = ler(post("/api/v1/professor/turmas/" + turmaId + "/atividades", prof, """
                {"titulo":"Atividade 1","tipo":"%s","duracaoMinutos":%s,"maxTentativas":%s,"questaoIds":["%s","%s"]}
                """.formatted(tipo, duracao, maxTentativas, q1, q2)).andExpect(status().isCreated()), "$.id");
        post("/api/v1/professor/atividades/" + id + "/publicar", prof, null).andExpect(status().isOk());
        return id;
    }

    private void entrarNaTurma(String aluno) throws Exception {
        post("/api/v1/aluno/turmas/entrar", aluno, "{\"codigo\":\"%s\"}".formatted(codigoConvite)).andExpect(status().isOk());
    }

    private String alternativa(ResultActions estado, int questao, int indice) throws Exception {
        return ler(estado, "$.questoes[%d].alternativas[%d].id".formatted(questao, indice));
    }

    private void marcar(String aluno, String tentativa, String questao, String alternativa) throws Exception {
        put("/api/v1/aluno/tentativas/" + tentativa + "/respostas/" + questao, aluno,
                "{\"alternativaId\":\"%s\"}".formatted(alternativa)).andExpect(status().isNoContent());
    }

    @Test
    void alunoEntraPeloCodigoEConviteRenovadoInvalidaOAntigo() throws Exception {
        post("/api/v1/aluno/turmas/entrar", ana, "{\"codigo\":\"XXX-XXX\"}").andExpect(status().isNotFound());
        // aceita minúsculas e sem hífen
        post("/api/v1/aluno/turmas/entrar", ana, "{\"codigo\":\"%s\"}".formatted(codigoConvite.replace("-", "").toLowerCase()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.professor").value("Prof. Silva"));
        entrarNaTurma(ana); // repetir não duplica

        get("/api/v1/professor/turmas/" + turmaId, prof)
                .andExpect(jsonPath("$.alunos", hasSize(1)))
                .andExpect(jsonPath("$.alunos[0].nome").value("Ana Souza"));

        post("/api/v1/professor/turmas/" + turmaId + "/convite", prof, null).andExpect(status().isOk());
        post("/api/v1/aluno/turmas/entrar", aluno("Bruno"), "{\"codigo\":\"%s\"}".formatted(codigoConvite))
                .andExpect(status().isNotFound());
    }

    @Test
    void professorNaoAcessaTurmaDeOutroProfessor() throws Exception {
        String outro = entrarComo("PROFESSOR", "Prof. Lima");
        get("/api/v1/professor/turmas/" + turmaId, outro).andExpect(status().isNotFound());
        get("/api/v1/professor/turmas/" + turmaId + "/resumo", outro).andExpect(status().isNotFound());
        get("/api/v1/professor/turmas", outro).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void painelDoProfessorResumeAsTurmasAtivas() throws Exception {
        entrarNaTurma(ana);
        String bruno = aluno("Bruno");
        entrarNaTurma(bruno);
        atividadePublicada("30", "1");

        // segunda turma com a mesma aluna: conta uma vez no total
        ResultActions segunda = criarTurma(prof);
        String segundaId = ler(segunda, "$.id");
        post("/api/v1/aluno/turmas/entrar", ana, "{\"codigo\":\"%s\"}".formatted((String) ler(segunda, "$.codigoConvite")))
                .andExpect(status().isOk());

        get("/api/v1/professor/painel", prof)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turmasAtivas").value(2))
                .andExpect(jsonPath("$.totalAlunos").value(2))
                .andExpect(jsonPath("$.atividadesAbertas").value(1))
                .andExpect(jsonPath("$.questoesNoBanco").value(2))
                .andExpect(jsonPath("$.turmas", hasSize(2)))
                .andExpect(jsonPath("$.atividades", hasSize(1)))
                .andExpect(jsonPath("$.atividades[0].turma.id").value(turmaId))
                .andExpect(jsonPath("$.atividades[0].totalAlunos").value(2))
                .andExpect(jsonPath("$.atividades[0].concluiram").value(0));

        // turma arquivada sai do painel, com as atividades dela
        post("/api/v1/professor/turmas/" + turmaId + "/arquivar", prof, null).andExpect(status().isOk());
        get("/api/v1/professor/painel", prof)
                .andExpect(jsonPath("$.turmasAtivas").value(1))
                .andExpect(jsonPath("$.turmas[0].id").value(segundaId))
                .andExpect(jsonPath("$.totalAlunos").value(1))
                .andExpect(jsonPath("$.atividadesAbertas").value(0))
                .andExpect(jsonPath("$.atividades", hasSize(0)));

        get("/api/v1/professor/painel", entrarComo("PROFESSOR", "Prof. Lima"))
                .andExpect(jsonPath("$.turmasAtivas").value(0))
                .andExpect(jsonPath("$.turmas", hasSize(0)));
    }

    @Test
    void professorRemoveAlunoQueDeixaDeVerATurmaEPodeVoltarPeloCodigo() throws Exception {
        entrarNaTurma(ana);
        atividadePublicada("30", "1");
        String anaId = ler(get("/api/v1/professor/turmas/" + turmaId, prof), "$.alunos[0].id");

        delete("/api/v1/professor/turmas/" + turmaId + "/alunos/" + anaId, prof).andExpect(status().isNoContent());
        delete("/api/v1/professor/turmas/" + turmaId + "/alunos/" + anaId, prof).andExpect(status().isNotFound());

        get("/api/v1/professor/turmas/" + turmaId, prof).andExpect(jsonPath("$.alunos", hasSize(0)));
        get("/api/v1/aluno/turmas", ana).andExpect(jsonPath("$", hasSize(0)));
        get("/api/v1/aluno/atividades", ana).andExpect(jsonPath("$", hasSize(0)));

        // outro professor não remove aluno da turma
        entrarNaTurma(ana);
        delete("/api/v1/professor/turmas/" + turmaId + "/alunos/" + anaId, entrarComo("PROFESSOR", "Prof. Lima"))
                .andExpect(status().isNotFound());
        get("/api/v1/professor/turmas/" + turmaId, prof).andExpect(jsonPath("$.alunos", hasSize(1)));
    }

    @Test
    void alunoSaiDaTurmaEPodeVoltarPeloCodigo() throws Exception {
        entrarNaTurma(ana);
        String atividade = atividadePublicada("30", "1");
        String tentativa = ler(post("/api/v1/aluno/atividades/" + atividade + "/tentativas", ana, null), "$.id");
        post("/api/v1/aluno/tentativas/" + tentativa + "/enviar", ana, null).andExpect(status().isOk());

        delete("/api/v1/aluno/turmas/" + turmaId, ana).andExpect(status().isNoContent());
        delete("/api/v1/aluno/turmas/" + turmaId, ana).andExpect(status().isNotFound());

        get("/api/v1/aluno/turmas", ana).andExpect(jsonPath("$", hasSize(0)));
        get("/api/v1/aluno/atividades", ana).andExpect(jsonPath("$", hasSize(0)));
        get("/api/v1/professor/turmas/" + turmaId, prof).andExpect(jsonPath("$.alunos", hasSize(0)));
        // a tentativa continua guardada
        get("/api/v1/aluno/tentativas", ana).andExpect(jsonPath("$", hasSize(1)));

        // quem nunca esteve na turma recebe 404
        delete("/api/v1/aluno/turmas/" + turmaId, aluno("Bruno")).andExpect(status().isNotFound());

        entrarNaTurma(ana);
        get("/api/v1/aluno/turmas", ana).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void turmaArquivadaSaiDaListaENaoAceitaAlunosNemAtividades() throws Exception {
        entrarNaTurma(ana);
        String rascunho = ler(post("/api/v1/professor/turmas/" + turmaId + "/atividades", prof, """
                {"titulo":"Lista","tipo":"LISTA","maxTentativas":1,"questaoIds":["%s"]}
                """.formatted(q1)).andExpect(status().isCreated()), "$.id");
        String publicada = atividadePublicada("30", "1");
        get("/api/v1/aluno/turmas", ana).andExpect(jsonPath("$", hasSize(1)));

        post("/api/v1/professor/turmas/" + turmaId + "/arquivar", prof, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arquivadaEm").isNotEmpty());

        // a atividade publicada é encerrada; o rascunho continua rascunho
        get("/api/v1/professor/atividades/" + publicada, prof).andExpect(jsonPath("$.atividade.status").value("ENCERRADA"));
        get("/api/v1/professor/atividades/" + rascunho, prof).andExpect(jsonPath("$.atividade.status").value("RASCUNHO"));
        post("/api/v1/aluno/atividades/" + publicada + "/tentativas", ana, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ATIVIDADE_ENCERRADA"));

        // a turma some da lista do aluno
        get("/api/v1/aluno/turmas", ana).andExpect(jsonPath("$", hasSize(0)));
        post("/api/v1/professor/turmas/" + turmaId + "/arquivar", prof, null).andExpect(status().isOk()); // repetir não dá erro

        get("/api/v1/professor/turmas", prof).andExpect(jsonPath("$", hasSize(0)));
        get("/api/v1/professor/turmas?incluirArquivadas=true", prof)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].arquivadaEm").isNotEmpty());

        // novo aluno é recusado; quem já estava continua entrando sem erro
        post("/api/v1/aluno/turmas/entrar", aluno("Bruno"), "{\"codigo\":\"%s\"}".formatted(codigoConvite))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TURMA_ARQUIVADA"));
        entrarNaTurma(ana);

        post("/api/v1/professor/turmas/" + turmaId + "/atividades", prof, """
                {"titulo":"Outra","tipo":"LISTA","maxTentativas":1,"questaoIds":["%s"]}
                """.formatted(q1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TURMA_ARQUIVADA"));
        post("/api/v1/professor/atividades/" + rascunho + "/publicar", prof, null)
                .andExpect(jsonPath("$.codigo").value("TURMA_ARQUIVADA"));
        post("/api/v1/professor/turmas/" + turmaId + "/convite", prof, null)
                .andExpect(jsonPath("$.codigo").value("TURMA_ARQUIVADA"));

        // alunos e histórico continuam guardados
        get("/api/v1/professor/turmas/" + turmaId, prof).andExpect(jsonPath("$.alunos", hasSize(1)));

        post("/api/v1/professor/turmas/" + turmaId + "/reativar", prof, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arquivadaEm").value(nullValue()));
        get("/api/v1/professor/turmas", prof).andExpect(jsonPath("$", hasSize(1)));
        get("/api/v1/aluno/turmas", ana).andExpect(jsonPath("$", hasSize(1)));
        get("/api/v1/professor/atividades/" + publicada, prof).andExpect(jsonPath("$.atividade.status").value("ENCERRADA"));
        post("/api/v1/professor/atividades/" + rascunho + "/publicar", prof, null).andExpect(status().isOk());

        // outro professor não arquiva a turma
        post("/api/v1/professor/turmas/" + turmaId + "/arquivar", entrarComo("PROFESSOR", "Prof. Lima"), null)
                .andExpect(status().isNotFound());
    }

    @Test
    void atividadeSoApareceParaAlunosDaTurmaDepoisDePublicada() throws Exception {
        String rascunho = ler(post("/api/v1/professor/turmas/" + turmaId + "/atividades", prof, """
                {"titulo":"Lista","tipo":"LISTA","maxTentativas":1,"questaoIds":["%s"]}
                """.formatted(q1)).andExpect(status().isCreated()), "$.id");
        entrarNaTurma(ana);
        get("/api/v1/aluno/atividades", ana).andExpect(jsonPath("$", hasSize(0)));

        post("/api/v1/professor/atividades/" + rascunho + "/publicar", prof, null).andExpect(status().isOk());
        get("/api/v1/aluno/atividades", ana)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].podeIniciar").value(true));

        // quem não está na turma não vê nem inicia
        String bruno = aluno("Bruno");
        get("/api/v1/aluno/atividades", bruno).andExpect(jsonPath("$", hasSize(0)));
        post("/api/v1/aluno/atividades/" + rascunho + "/tentativas", bruno, null).andExpect(status().isNotFound());

        // publicada não pode mais ser alterada
        put("/api/v1/professor/atividades/" + rascunho, prof, """
                {"titulo":"Outra","tipo":"LISTA","questaoIds":["%s"]}
                """.formatted(q2)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void tentativaCompleta_gabaritoSoDepoisDeEnviar() throws Exception {
        String atividade = atividadePublicada("30", "1");
        entrarNaTurma(ana);

        ResultActions estado = post("/api/v1/aluno/atividades/" + atividade + "/tentativas", ana, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questoes", hasSize(2)))
                .andExpect(jsonPath("$.questoes[0].alternativas[0].correta").doesNotExist())
                .andExpect(jsonPath("$.questoes[0].explicacao").doesNotExist())
                .andExpect(jsonPath("$.segundosRestantes").isNumber());
        String tentativa = ler(estado, "$.id");

        get("/api/v1/aluno/tentativas/" + tentativa + "/resultado", ana)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TENTATIVA_EM_ANDAMENTO"));

        marcar(ana, tentativa, q1, alternativa(estado, 0, 0)); // errada
        marcar(ana, tentativa, q1, alternativa(estado, 0, 2)); // troca para a certa
        // q2 fica em branco

        // retomar (ex.: trocou de aparelho) devolve a mesma tentativa com a resposta marcada
        post("/api/v1/aluno/atividades/" + atividade + "/tentativas", ana, null)
                .andExpect(jsonPath("$.id").value(tentativa))
                .andExpect(jsonPath("$.respostas['" + q1 + "']").value(alternativa(estado, 0, 2)));

        post("/api/v1/aluno/tentativas/" + tentativa + "/enviar", ana, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tentativa.acertos").value(1))
                .andExpect(jsonPath("$.tentativa.total").value(2))
                .andExpect(jsonPath("$.tentativa.encerramento").value("ENVIO"))
                .andExpect(jsonPath("$.questoes[0].acertou").value(true))
                .andExpect(jsonPath("$.questoes[0].explicacao").isNotEmpty())
                .andExpect(jsonPath("$.questoes[1].acertou").value(false))
                .andExpect(jsonPath("$.questoes[1].alternativaMarcadaId").doesNotExist())
                .andExpect(jsonPath("$.porTema", hasSize(2)));

        // depois de enviar não dá para alterar respostas nem iniciar outra (1 tentativa)
        put("/api/v1/aluno/tentativas/" + tentativa + "/respostas/" + q2, ana, "{\"alternativaId\":null}")
                .andExpect(status().isUnprocessableEntity());
        post("/api/v1/aluno/atividades/" + atividade + "/tentativas", ana, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SEM_TENTATIVAS"));

        // outro aluno não enxerga a tentativa
        get("/api/v1/aluno/tentativas/" + tentativa + "/resultado", aluno("Bruno")).andExpect(status().isNotFound());

        get("/api/v1/aluno/desempenho", ana)
                .andExpect(jsonPath("$.respondidas").value(2))
                .andExpect(jsonPath("$.percentual").value(50))
                .andExpect(jsonPath("$.melhorTema").value("Banco de Dados"));
    }

    @Test
    void praticaLivreNaoEntregaGabaritoDeAtividadeAindaNaoConcluida() throws Exception {
        String atividade = atividadePublicada("30", "1");
        entrarNaTurma(ana);
        String bruno = aluno("Bruno"); // fora da turma: a atividade não o afeta

        // antes mesmo de iniciar, as questões da atividade saem da prática livre da Ana
        get("/api/v1/aluno/pratica/questoes", ana).andExpect(jsonPath("$.total").value(0));
        get("/api/v1/aluno/pratica/questoes", bruno).andExpect(jsonPath("$.total").value(2));

        ResultActions estado = post("/api/v1/aluno/atividades/" + atividade + "/tentativas", ana, null);
        String tentativa = ler(estado, "$.id");
        String corpo = "{\"alternativaId\":\"%s\"}".formatted(alternativa(estado, 0, 0));

        // com a tentativa em andamento, responder pela prática não devolve o gabarito
        post("/api/v1/aluno/pratica/questoes/" + q1 + "/responder", ana, corpo)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("QUESTAO_EM_ATIVIDADE"))
                .andExpect(jsonPath("$.alternativaCorretaId").doesNotExist());
        post("/api/v1/aluno/pratica/questoes/" + q1 + "/responder", bruno, corpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alternativaCorretaId").isNotEmpty());

        // o professor encerra a atividade, mas a tentativa da Ana continua: segue bloqueada
        post("/api/v1/professor/atividades/" + atividade + "/encerrar", prof, null).andExpect(status().isOk());
        post("/api/v1/aluno/pratica/questoes/" + q1 + "/responder", ana, corpo)
                .andExpect(status().isUnprocessableEntity());

        // depois de enviar, ela já viu o gabarito: as questões voltam para a prática
        post("/api/v1/aluno/tentativas/" + tentativa + "/enviar", ana, null).andExpect(status().isOk());
        get("/api/v1/aluno/pratica/questoes", ana).andExpect(jsonPath("$.total").value(2));
        post("/api/v1/aluno/pratica/questoes/" + q1 + "/responder", ana, corpo).andExpect(status().isOk());
    }

    @Test
    void fimDoTempoEnviaAutomaticamenteOQueEstavaMarcado() throws Exception {
        String atividade = atividadePublicada("LISTA", "30", "null");
        entrarNaTurma(ana);
        ResultActions estado = post("/api/v1/aluno/atividades/" + atividade + "/tentativas", ana, null);
        String tentativa = ler(estado, "$.id");
        marcar(ana, tentativa, q2, alternativa(estado, 1, 1)); // certa

        // simula o cronômetro chegando a zero
        jdbc.update("update tentativa set iniciada_em = now() - interval '31 minutes', expira_em = now() - interval '1 minute'");

        put("/api/v1/aluno/tentativas/" + tentativa + "/respostas/" + q1, ana,
                "{\"alternativaId\":\"%s\"}".formatted(alternativa(estado, 0, 2)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TENTATIVA_ENCERRADA"));

        get("/api/v1/aluno/tentativas/" + tentativa + "/resultado", ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tentativa.encerramento").value("TEMPO"))
                .andExpect(jsonPath("$.tentativa.acertos").value(1))
                .andExpect(jsonPath("$.tempoSegundos").value(30 * 60));

        // tentativas ilimitadas: pode começar outra, com numeração seguinte
        post("/api/v1/aluno/atividades/" + atividade + "/tentativas", ana, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.numero").value(2));
    }

    /** Corpo de criação com o tipo e o limite de tentativas informados. */
    private String corpoAtividade(String tipo, String maxTentativas) {
        return """
                {"titulo":"Regra","tipo":"%s","maxTentativas":%s,"questaoIds":["%s"]}
                """.formatted(tipo, maxTentativas, q1);
    }

    @Test
    void listaAceitaDeUmACincoTentativasOuIlimitadas() throws Exception {
        String rota = "/api/v1/professor/turmas/" + turmaId + "/atividades";
        for (String max : new String[]{"1", "5", "null"}) {
            post(rota, prof, corpoAtividade("LISTA", max)).andExpect(status().isCreated());
        }
        // acima de 5 o contrato já recusa
        post(rota, prof, corpoAtividade("LISTA", "6")).andExpect(status().isBadRequest());
    }

    @Test
    void simuladoAceitaNoMaximoDuasTentativasESemIlimitadas() throws Exception {
        String rota = "/api/v1/professor/turmas/" + turmaId + "/atividades";
        post(rota, prof, corpoAtividade("SIMULADO", "1")).andExpect(status().isCreated());
        String rascunho = ler(post(rota, prof, corpoAtividade("SIMULADO", "2")).andExpect(status().isCreated()), "$.id");

        for (String max : new String[]{"3", "null"}) {
            post(rota, prof, corpoAtividade("SIMULADO", max))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("TENTATIVAS_INVALIDAS"));
        }
        // a edição segue a mesma regra
        put("/api/v1/professor/atividades/" + rascunho, prof, corpoAtividade("SIMULADO", "null"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TENTATIVAS_INVALIDAS"));
    }

    @Test
    void rascunhoAntigoForaDaRegraNaoPublica() throws Exception {
        String rascunho = ler(post("/api/v1/professor/turmas/" + turmaId + "/atividades", prof,
                corpoAtividade("LISTA", "null")).andExpect(status().isCreated()), "$.id");
        // simula um simulado ilimitado gravado antes da regra
        jdbc.update("update atividade set tipo = 'SIMULADO' where id = ?::uuid", rascunho);

        post("/api/v1/professor/atividades/" + rascunho + "/publicar", prof, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TENTATIVAS_INVALIDAS"));
    }

    @Test
    void professorAcompanhaResultadosDaTurmaEDoAluno() throws Exception {
        String atividade = atividadePublicada("null", "2");
        String bruno = aluno("Bruno Lima");
        entrarNaTurma(ana);
        entrarNaTurma(bruno);

        // Ana: 1ª tentativa zera, 2ª acerta tudo → vale a melhor
        ResultActions e1 = post("/api/v1/aluno/atividades/" + atividade + "/tentativas", ana, null);
        post("/api/v1/aluno/tentativas/" + ler(e1, "$.id") + "/enviar", ana, null).andExpect(jsonPath("$.tentativa.acertos").value(0));
        ResultActions e2 = post("/api/v1/aluno/atividades/" + atividade + "/tentativas", ana, null);
        String t2 = ler(e2, "$.id");
        marcar(ana, t2, q1, alternativa(e2, 0, 2));
        marcar(ana, t2, q2, alternativa(e2, 1, 1));
        post("/api/v1/aluno/tentativas/" + t2 + "/enviar", ana, null).andExpect(jsonPath("$.tentativa.acertos").value(2));

        get("/api/v1/professor/atividades/" + atividade + "/resultados", prof)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAlunos").value(2))
                .andExpect(jsonPath("$.concluiram").value(1))
                .andExpect(jsonPath("$.media").value(10.0))
                .andExpect(jsonPath("$.alunos[0].nome").value("Ana Souza"))
                .andExpect(jsonPath("$.alunos[0].acertos").value(2))
                .andExpect(jsonPath("$.alunos[0].tentativas").value(2))
                .andExpect(jsonPath("$.alunos[1].nome").value("Bruno Lima"))
                .andExpect(jsonPath("$.alunos[1].acertos").doesNotExist())
                .andExpect(jsonPath("$.porTema", hasSize(2)));

        get("/api/v1/professor/turmas/" + turmaId + "/resumo", prof)
                .andExpect(jsonPath("$.totalAlunos").value(2))
                .andExpect(jsonPath("$.questoesNoBanco").value(2))
                .andExpect(jsonPath("$.atividadesAbertas").value(1))
                .andExpect(jsonPath("$.mediaPercentual").value(50))
                .andExpect(jsonPath("$.atividades[0].concluiram").value(1));

        String anaId = ler(get("/api/v1/eu", ana), "$.id");
        get("/api/v1/professor/turmas/" + turmaId + "/alunos/" + anaId + "/desempenho", prof)
                .andExpect(jsonPath("$.tentativas", hasSize(2)))
                .andExpect(jsonPath("$.porTema", hasSize(2)));

        // encerrada: ninguém inicia nova tentativa
        post("/api/v1/professor/atividades/" + atividade + "/encerrar", prof, null).andExpect(status().isOk());
        post("/api/v1/aluno/atividades/" + atividade + "/tentativas", bruno, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ATIVIDADE_ENCERRADA"));
    }
}
