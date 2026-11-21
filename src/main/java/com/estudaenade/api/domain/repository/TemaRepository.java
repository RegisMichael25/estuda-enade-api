package com.estudaenade.api.domain.repository;

import com.estudaenade.api.domain.model.Tema;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemaRepository extends JpaRepository<Tema, UUID> {

    List<Tema> findAllByOrderByNomeAsc();

    Optional<Tema> findByNomeIgnoreCase(String nome);
}
