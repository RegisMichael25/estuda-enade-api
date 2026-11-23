package com.estudaenade.api.domain.repository;

import com.estudaenade.api.domain.model.ComentarioAtividade;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ComentarioAtividadeRepository extends JpaRepository<ComentarioAtividade, UUID> {

    @EntityGraph(attributePaths = "autor")
    List<ComentarioAtividade> findByAtividadeIdOrderByCriadoEmAscIdAsc(UUID atividadeId);
}
