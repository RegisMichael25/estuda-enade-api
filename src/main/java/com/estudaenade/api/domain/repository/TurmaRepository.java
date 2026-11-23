package com.estudaenade.api.domain.repository;

import com.estudaenade.api.domain.model.Turma;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TurmaRepository extends JpaRepository<Turma, UUID> {

    List<Turma> findByProfessorIdOrderByCriadaEmDesc(UUID professorId);

    List<Turma> findByProfessorIdAndArquivadaEmIsNullOrderByCriadaEmDesc(UUID professorId);

    Optional<Turma> findByIdAndProfessorId(UUID id, UUID professorId);

    Optional<Turma> findByCodigoConvite(String codigoConvite);

    boolean existsByCodigoConvite(String codigoConvite);
}
