package com.estudaenade.api.api.dto;

import com.estudaenade.api.domain.model.Participacao;
import com.estudaenade.api.domain.model.Turma;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Respostas desta área. As requisições são geradas do contrato OpenAPI (pacote {@code api.contrato.dto}). */
public final class TurmaDtos {

    private TurmaDtos() {
    }

    /** Visão do professor: inclui o código de convite. */
    public record TurmaResponse(UUID id, String nome, String periodo, ItemResponse curso,
                                String codigoConvite, long totalAlunos, Instant criadaEm) {
        public static TurmaResponse de(Turma t, long totalAlunos) {
            return new TurmaResponse(t.getId(), t.getNome(), t.getPeriodo(), ItemResponse.de(t.getCurso()),
                    t.getCodigoConvite(), totalAlunos, t.getCriadaEm());
        }
    }

    public record AlunoResponse(UUID id, String nome, String email, Instant entrouEm) {
        public static AlunoResponse de(Participacao p) {
            return new AlunoResponse(p.getAluno().getId(), p.getAluno().getNome(), p.getAluno().getEmail(), p.getEntrouEm());
        }
    }

    public record TurmaDetalheResponse(TurmaResponse turma, List<AlunoResponse> alunos) {
    }

    /** Visão do aluno: sem código de convite. */
    public record TurmaAlunoResponse(UUID id, String nome, String periodo, ItemResponse curso, String professor) {
        public static TurmaAlunoResponse de(Turma t) {
            return new TurmaAlunoResponse(t.getId(), t.getNome(), t.getPeriodo(), ItemResponse.de(t.getCurso()),
                    t.getProfessor().getNome());
        }
    }
}
