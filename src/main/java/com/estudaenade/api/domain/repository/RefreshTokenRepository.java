package com.estudaenade.api.domain.repository;

import com.estudaenade.api.domain.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Sem clearAutomatically, para o Usuario da mesma transação continuar gerenciado. */
    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revogado = true where t.usuario.id = :usuarioId and t.revogado = false")
    void revogarTodosDoUsuario(UUID usuarioId);
}
