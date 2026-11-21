package com.estudaenade.api.domain.repository;

import com.estudaenade.api.domain.model.Curso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CursoRepository extends JpaRepository<Curso, UUID> {

    List<Curso> findAllByOrderByNomeAsc();

    Optional<Curso> findByNomeIgnoreCase(String nome);
}
