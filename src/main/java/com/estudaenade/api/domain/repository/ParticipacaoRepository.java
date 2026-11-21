package com.estudaenade.api.domain.repository;

import com.estudaenade.api.domain.model.Participacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ParticipacaoRepository extends JpaRepository<Participacao, UUID> {

    List<Participacao> findByTurmaIdOrderByAlunoNomeAsc(UUID turmaId);

    List<Participacao> findByAlunoIdOrderByEntrouEmDesc(UUID alunoId);

    boolean existsByTurmaIdAndAlunoId(UUID turmaId, UUID alunoId);

    long countByTurmaId(UUID turmaId);

    long deleteByTurmaIdAndAlunoId(UUID turmaId, UUID alunoId);
}
