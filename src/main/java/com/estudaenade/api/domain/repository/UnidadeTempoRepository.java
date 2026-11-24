package com.estudaenade.api.domain.repository;

import com.estudaenade.api.domain.model.UnidadeTempo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UnidadeTempoRepository extends JpaRepository<UnidadeTempo, UUID> {

    /** Pelo id fixo da V00009: Minutos ({@code ...0007-...01}) antes de Horas ({@code ...02}). */
    List<UnidadeTempo> findAllByOrderByIdAsc();
}
