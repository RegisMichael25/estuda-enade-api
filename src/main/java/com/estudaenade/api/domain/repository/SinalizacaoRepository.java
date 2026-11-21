package com.estudaenade.api.domain.repository;

import com.estudaenade.api.domain.model.Sinalizacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SinalizacaoRepository extends JpaRepository<Sinalizacao, UUID> {

    List<Sinalizacao> findByQuestaoIdOrderByCriadaEmDesc(UUID questaoId);

    boolean existsByQuestaoIdAndUsuarioIdAndResolvidaFalse(UUID questaoId, UUID usuarioId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Sinalizacao s set s.resolvida = true, s.resolvidaEm = :agora where s.questao.id = :questaoId and s.resolvida = false")
    int resolverAbertas(UUID questaoId, Instant agora);
}
