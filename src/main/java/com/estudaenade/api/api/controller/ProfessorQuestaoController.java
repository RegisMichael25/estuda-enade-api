package com.estudaenade.api.api.controller;

import com.estudaenade.api.api.contrato.ProfessorQuestaoApi;
import com.estudaenade.api.api.contrato.dto.QuestaoRequest;
import com.estudaenade.api.api.contrato.dto.SinalizacaoRequest;
import com.estudaenade.api.api.dto.Pagina;
import com.estudaenade.api.api.dto.QuestaoDtos.QuestaoResponse;
import com.estudaenade.api.api.dto.QuestaoDtos.SinalizacaoResponse;
import com.estudaenade.api.domain.enums.StatusQuestao;
import com.estudaenade.api.domain.service.QuestaoService;
import com.estudaenade.api.infra.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Banco de questões visto pelo professor. Rotas em {@link ProfessorQuestaoApi}. */
@RestController
@RequiredArgsConstructor
public class ProfessorQuestaoController implements ProfessorQuestaoApi {

    private final QuestaoService questaoService;

    /** Anos das questões visíveis no banco, para o filtro. */
    @Override
    public List<Integer> anosDoBanco(UUID cursoId) {
        return questaoService.anosDoBanco(professor(), cursoId);
    }

    /** Publicadas de todos os professores + as minhas em qualquer estado. */
    @Override
    public Pagina<QuestaoResponse> listarQuestoes(Boolean minhas, UUID cursoId, UUID temaId, Integer ano, String busca,
                                                  StatusQuestao status, Boolean sinalizadas,
                                                  Integer pagina, Integer tamanho) {
        return questaoService.listar(professor(),
                new QuestaoService.Filtro(minhas, cursoId, temaId, ano, busca, status, sinalizadas), pagina, tamanho);
    }

    /** Salva como rascunho. */
    @Override
    public QuestaoResponse criarQuestao(QuestaoRequest req) {
        return questaoService.criar(professor(), req);
    }

    @Override
    public QuestaoResponse detalharQuestao(UUID questaoId) {
        return questaoService.detalhar(professor(), questaoId);
    }

    @Override
    public QuestaoResponse atualizarQuestao(UUID questaoId, QuestaoRequest req) {
        return questaoService.atualizar(professor(), questaoId, req);
    }

    @Override
    public void excluirQuestao(UUID questaoId) {
        questaoService.excluirRascunho(professor(), questaoId);
    }

    @Override
    public QuestaoResponse publicarQuestao(UUID questaoId) {
        return questaoService.publicar(professor(), questaoId);
    }

    @Override
    public QuestaoResponse ocultarQuestao(UUID questaoId) {
        return questaoService.ocultar(professor(), questaoId);
    }

    @Override
    public List<SinalizacaoResponse> sinalizacoesDaQuestao(UUID questaoId) {
        return questaoService.sinalizacoes(professor(), questaoId);
    }

    @Override
    public void sinalizarQuestao(UUID questaoId, SinalizacaoRequest req) {
        questaoService.sinalizar(UsuarioLogado.atual(), questaoId, req == null ? null : req.getMotivo());
    }

    /** O autor marca as sinalizações abertas como resolvidas. */
    @Override
    public QuestaoResponse resolverSinalizacoes(UUID questaoId) {
        return questaoService.resolverSinalizacoes(professor(), questaoId);
    }

    private static UUID professor() {
        return UsuarioLogado.atual().id();
    }
}
