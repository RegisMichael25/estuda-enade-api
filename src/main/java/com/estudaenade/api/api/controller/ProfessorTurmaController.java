package com.estudaenade.api.api.controller;

import com.estudaenade.api.api.contrato.ProfessorTurmaApi;
import com.estudaenade.api.api.contrato.dto.AtividadeRequest;
import com.estudaenade.api.api.contrato.dto.TurmaRequest;
import com.estudaenade.api.api.dto.AtividadeDtos.AtividadeResponse;
import com.estudaenade.api.api.dto.DesempenhoDtos.DesempenhoAlunoTurmaResponse;
import com.estudaenade.api.api.dto.DesempenhoDtos.ResumoTurmaResponse;
import com.estudaenade.api.api.dto.TurmaDtos.TurmaDetalheResponse;
import com.estudaenade.api.api.dto.TurmaDtos.TurmaResponse;
import com.estudaenade.api.domain.service.AtividadeService;
import com.estudaenade.api.domain.service.DesempenhoService;
import com.estudaenade.api.domain.service.TurmaService;
import com.estudaenade.api.infra.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Turmas do professor, seus alunos e as atividades de cada turma. Rotas em {@link ProfessorTurmaApi}. */
@RestController
@RequiredArgsConstructor
public class ProfessorTurmaController implements ProfessorTurmaApi {

    private final TurmaService turmaService;
    private final AtividadeService atividadeService;
    private final DesempenhoService desempenhoService;

    @Override
    public TurmaResponse criarTurma(TurmaRequest req) {
        return turmaService.criar(professor(), req);
    }

    @Override
    public List<TurmaResponse> listarTurmas() {
        return turmaService.listarDoProfessor(professor());
    }

    @Override
    public TurmaDetalheResponse detalharTurma(UUID turmaId) {
        return turmaService.detalhar(professor(), turmaId);
    }

    @Override
    public TurmaResponse atualizarTurma(UUID turmaId, TurmaRequest req) {
        return turmaService.atualizar(professor(), turmaId, req);
    }

    /** Troca o código de convite; o antigo deixa de funcionar. */
    @Override
    public TurmaResponse renovarConvite(UUID turmaId) {
        return turmaService.renovarConvite(professor(), turmaId);
    }

    @Override
    public void removerAluno(UUID turmaId, UUID alunoId) {
        turmaService.removerAluno(professor(), turmaId, alunoId);
    }

    @Override
    public DesempenhoAlunoTurmaResponse desempenhoDoAluno(UUID turmaId, UUID alunoId) {
        return desempenhoService.doAlunoNaTurma(professor(), turmaId, alunoId);
    }

    /** Números do painel inicial: alunos, banco, atividades abertas e média da turma. */
    @Override
    public ResumoTurmaResponse resumoDaTurma(UUID turmaId) {
        return desempenhoService.resumoDaTurma(professor(), turmaId);
    }

    @Override
    public List<AtividadeResponse> atividadesDaTurma(UUID turmaId) {
        return atividadeService.listarDaTurma(professor(), turmaId);
    }

    @Override
    public AtividadeResponse criarAtividade(UUID turmaId, AtividadeRequest req) {
        return atividadeService.criar(professor(), turmaId, req);
    }

    private static UUID professor() {
        return UsuarioLogado.atual().id();
    }
}
