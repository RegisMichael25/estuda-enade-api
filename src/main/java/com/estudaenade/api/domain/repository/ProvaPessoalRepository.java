package com.estudaenade.api.domain.repository;

import com.estudaenade.api.domain.model.ProvaPessoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProvaPessoalRepository extends JpaRepository<ProvaPessoal, UUID> {

    List<ProvaPessoal> findByAlunoIdOrderByCriadaEmDesc(UUID alunoId);

    Optional<ProvaPessoal> findByIdAndAlunoId(UUID id, UUID alunoId);
}
