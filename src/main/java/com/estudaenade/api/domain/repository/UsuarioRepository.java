package com.estudaenade.api.domain.repository;

import com.estudaenade.api.domain.enums.Papel;
import com.estudaenade.api.domain.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);

    List<Usuario> findByPapelAndAprovadoFalseOrderByCriadoEmAsc(Papel papel);
}
