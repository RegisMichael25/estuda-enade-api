package com.estudaenade.api.domain.repository;

import com.estudaenade.api.domain.model.ConviteProfessor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConviteProfessorRepository extends JpaRepository<ConviteProfessor, UUID> {

    /** Trava a linha até o fim da transação: dois cadastros simultâneos não usam o mesmo convite. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ConviteProfessor> findByCodigoHash(String codigoHash);

    List<ConviteProfessor> findByCriadoPorIdOrderByCriadoEmDesc(UUID criadoPorId);
}
