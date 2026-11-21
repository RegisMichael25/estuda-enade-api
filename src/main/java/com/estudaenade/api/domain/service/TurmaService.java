package com.estudaenade.api.domain.service;

import com.estudaenade.api.api.contrato.dto.TurmaRequest;
import com.estudaenade.api.api.dto.TurmaDtos.*;
import com.estudaenade.api.domain.model.Curso;
import com.estudaenade.api.domain.model.Participacao;
import com.estudaenade.api.domain.model.Turma;
import com.estudaenade.api.domain.repository.CursoRepository;
import com.estudaenade.api.domain.repository.ParticipacaoRepository;
import com.estudaenade.api.domain.repository.TurmaRepository;
import com.estudaenade.api.domain.repository.UsuarioRepository;
import com.estudaenade.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TurmaService {

    // Sem I, O, 0 e 1: evita confusão ao digitar o código
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TurmaRepository turmaRepository;
    private final ParticipacaoRepository participacaoRepository;
    private final CursoRepository cursoRepository;
    private final UsuarioRepository usuarioRepository;

    // ======================== professor ========================

    public TurmaResponse criar(UUID professorId, TurmaRequest req) {
        Turma turma = turmaRepository.save(Turma.builder()
                .nome(req.getNome().trim())
                .periodo(req.getPeriodo())
                .curso(curso(req.getCursoId()))
                .professor(usuarioRepository.getReferenceById(professorId))
                .codigoConvite(novoCodigo())
                .build());
        return TurmaResponse.de(turma, 0);
    }

    @Transactional(readOnly = true)
    public List<TurmaResponse> listarDoProfessor(UUID professorId) {
        return turmaRepository.findByProfessorIdOrderByCriadaEmDesc(professorId).stream()
                .map(t -> TurmaResponse.de(t, participacaoRepository.countByTurmaId(t.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TurmaDetalheResponse detalhar(UUID professorId, UUID turmaId) {
        Turma turma = doProfessor(turmaId, professorId);
        List<AlunoResponse> alunos = participacaoRepository.findByTurmaIdOrderByAlunoNomeAsc(turmaId).stream()
                .map(AlunoResponse::de).toList();
        return new TurmaDetalheResponse(TurmaResponse.de(turma, alunos.size()), alunos);
    }

    public TurmaResponse atualizar(UUID professorId, UUID turmaId, TurmaRequest req) {
        Turma turma = doProfessor(turmaId, professorId);
        turma.setNome(req.getNome().trim());
        turma.setPeriodo(req.getPeriodo());
        turma.setCurso(curso(req.getCursoId()));
        return TurmaResponse.de(turma, participacaoRepository.countByTurmaId(turmaId));
    }

    /** Gera um novo código de convite; o anterior deixa de funcionar. Quem já entrou continua na turma. */
    public TurmaResponse renovarConvite(UUID professorId, UUID turmaId) {
        Turma turma = doProfessor(turmaId, professorId);
        turma.setCodigoConvite(novoCodigo());
        return TurmaResponse.de(turma, participacaoRepository.countByTurmaId(turmaId));
    }

    public void removerAluno(UUID professorId, UUID turmaId, UUID alunoId) {
        doProfessor(turmaId, professorId);
        if (participacaoRepository.deleteByTurmaIdAndAlunoId(turmaId, alunoId) == 0) {
            throw ApiException.naoEncontrado("Aluno não está nesta turma.");
        }
    }

    /** Turma do professor, ou 404 — turmas de outros professores não são reveladas. */
    public Turma doProfessor(UUID turmaId, UUID professorId) {
        return turmaRepository.findByIdAndProfessorId(turmaId, professorId)
                .orElseThrow(() -> ApiException.naoEncontrado("Turma não encontrada."));
    }

    // ======================== aluno ========================

    /** Entra na turma pelo código do convite. Repetir a entrada não gera erro. */
    public TurmaAlunoResponse entrar(UUID alunoId, String codigo) {
        Turma turma = turmaRepository.findByCodigoConvite(normalizarCodigo(codigo))
                .orElseThrow(() -> ApiException.naoEncontrado("Código de turma inválido. Confira com seu professor."));
        if (!participacaoRepository.existsByTurmaIdAndAlunoId(turma.getId(), alunoId)) {
            participacaoRepository.save(Participacao.builder()
                    .turma(turma).aluno(usuarioRepository.getReferenceById(alunoId)).build());
        }
        return TurmaAlunoResponse.de(turma);
    }

    @Transactional(readOnly = true)
    public List<TurmaAlunoResponse> listarDoAluno(UUID alunoId) {
        return participacaoRepository.findByAlunoIdOrderByEntrouEmDesc(alunoId).stream()
                .map(p -> TurmaAlunoResponse.de(p.getTurma())).toList();
    }

    // ======================== apoio ========================

    private Curso curso(UUID cursoId) {
        if (cursoId == null) return null;
        return cursoRepository.findById(cursoId).orElseThrow(() -> ApiException.naoEncontrado("Curso não encontrado."));
    }

    private String novoCodigo() {
        for (int i = 0; i < 20; i++) {
            StringBuilder sb = new StringBuilder(7);
            for (int j = 0; j < 6; j++) {
                if (j == 3) sb.append('-');
                sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
            }
            if (!turmaRepository.existsByCodigoConvite(sb.toString())) return sb.toString();
        }
        throw new IllegalStateException("Não foi possível gerar um código de convite único.");
    }

    /** Aceita o código em minúsculas, sem hífen ou com espaços em volta. */
    static String normalizarCodigo(String codigo) {
        String limpo = codigo.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return limpo.length() == 6 ? limpo.substring(0, 3) + "-" + limpo.substring(3) : limpo;
    }
}
