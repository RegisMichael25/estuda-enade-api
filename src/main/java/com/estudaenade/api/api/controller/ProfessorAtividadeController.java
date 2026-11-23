package com.estudaenade.api.api.controller;

import com.estudaenade.api.api.contrato.ProfessorAtividadeApi;
import com.estudaenade.api.api.contrato.dto.ComentarioRequest;
import com.estudaenade.api.api.dto.ComentarioDtos.ComentarioResponse;
import com.estudaenade.api.api.dto.ComentarioDtos.MuralResponse;
import com.estudaenade.api.domain.service.ComentarioService;
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
    private final ComentarioService comentarioService;

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

    // ---------- mural de comentários ----------

    @Override
    public MuralResponse muralDaAtividade(UUID atividadeId) {
        return comentarioService.mural(UsuarioLogado.atual(), atividadeId);
    }

    @Override
    public ComentarioResponse comentarNaAtividade(UUID atividadeId, ComentarioRequest req) {
        return comentarioService.comentar(UsuarioLogado.atual(), atividadeId, req.getTexto());
    }

    @Override
    public ComentarioResponse editarComentario(UUID comentarioId, ComentarioRequest req) {
        return comentarioService.editar(UsuarioLogado.atual(), comentarioId, req.getTexto());
    }

    @Override
    public void apagarComentario(UUID comentarioId) {
        comentarioService.apagar(UsuarioLogado.atual(), comentarioId);
    }

    private static UUID professor() {
        return UsuarioLogado.atual().id();
    }
}
