package com.estudaenade.api.domain.repository;

import com.estudaenade.api.domain.model.RespostaTentativa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RespostaTentativaRepository extends JpaRepository<RespostaTentativa, UUID> {

    List<RespostaTentativa> findByTentativaId(UUID tentativaId);

    Optional<RespostaTentativa> findByTentativaIdAndQuestaoId(UUID tentativaId, UUID questaoId);
}
