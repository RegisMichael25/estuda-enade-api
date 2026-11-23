package com.estudaenade.api.domain.repository;

import com.estudaenade.api.domain.model.Tentativa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TentativaRepository extends JpaRepository<Tentativa, UUID> {

    Optional<Tentativa> findByIdAndAlunoId(UUID id, UUID alunoId);

    Optional<Tentativa> findByAtividadeIdAndAlunoIdAndFinalizadaEmIsNull(UUID atividadeId, UUID alunoId);

    long countByAtividadeIdAndAlunoId(UUID atividadeId, UUID alunoId);

    boolean existsByAtividadeIdAndAlunoIdAndFinalizadaEmIsNotNull(UUID atividadeId, UUID alunoId);

    List<Tentativa> findByAlunoIdOrderByIniciadaEmDesc(UUID alunoId);

    List<Tentativa> findByFinalizadaEmIsNullAndExpiraEmLessThanEqual(Instant agora);
}
