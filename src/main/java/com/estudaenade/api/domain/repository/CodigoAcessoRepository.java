package com.estudaenade.api.domain.repository;

import com.estudaenade.api.domain.model.CodigoAcesso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CodigoAcessoRepository extends JpaRepository<CodigoAcesso, UUID> {

    long countByEmailAndCriadoEmAfter(String email, Instant desde);

    Optional<CodigoAcesso> findFirstByEmailAndUsadoFalseAndExpiraEmAfterOrderByCriadoEmDesc(String email, Instant agora);

    /** Invalida os códigos pendentes do e-mail (mantém as linhas para o limite de solicitações). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CodigoAcesso c set c.usado = true where c.email = :email and c.usado = false")
    void invalidarPendentes(String email);
}
