package com.estudaenade.api.domain.repository;

import com.estudaenade.api.domain.model.Atividade;
import org.springframework.data.jpa.repository.JpaRepository;
import com.estudaenade.api.domain.enums.StatusAtividade;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AtividadeRepository extends JpaRepository<Atividade, UUID> {

    List<Atividade> findByTurmaIdOrderByCriadaEmDesc(UUID turmaId);

    List<Atividade> findByTurmaIdInAndStatusInOrderByPublicadaEmDesc(Collection<UUID> turmaIds, Collection<StatusAtividade> status);
}
