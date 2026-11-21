package com.estudaenade.api.api.controller;

import com.estudaenade.api.api.contrato.ProfessorAtividadeApi;
import com.estudaenade.api.api.contrato.dto.AtividadeRequest;
import com.estudaenade.api.api.dto.AtividadeDtos.AtividadeDetalheResponse;
import com.estudaenade.api.api.dto.AtividadeDtos.AtividadeResponse;
import com.estudaenade.api.api.dto.DesempenhoDtos.ResultadosAtividadeResponse;
import com.estudaenade.api.domain.service.AtividadeService;
import com.estudaenade.api.domain.service.DesempenhoService;
import com.estudaenade.api.infra.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Listas e simulados do professor. Rotas em {@link ProfessorAtividadeApi}. */
@RestController
@RequiredArgsConstructor
public class ProfessorAtividadeController implements ProfessorAtividadeApi {

    private final AtividadeService atividadeService;
    private final DesempenhoService desempenhoService;

    @Override
    public AtividadeDetalheResponse detalharAtividade(UUID atividadeId) {
        return atividadeService.detalhar(professor(), atividadeId);
    }

    @Override
    public AtividadeResponse atualizarAtividade(UUID atividadeId, AtividadeRequest req) {
        return atividadeService.atualizar(professor(), atividadeId, req);
    }

    @Override
    public void excluirAtividade(UUID atividadeId) {
        atividadeService.excluirRascunho(professor(), atividadeId);
    }

    @Override
    public AtividadeResponse publicarAtividade(UUID atividadeId) {
        return atividadeService.publicar(professor(), atividadeId);
    }

    @Override
    public AtividadeResponse encerrarAtividade(UUID atividadeId) {
        return atividadeService.encerrar(professor(), atividadeId);
    }

    @Override
    public ResultadosAtividadeResponse resultadosDaAtividade(UUID atividadeId) {
        return desempenhoService.resultadosDaAtividade(professor(), atividadeId);
    }

    private static UUID professor() {
        return UsuarioLogado.atual().id();
    }
}
