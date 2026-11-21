package com.estudaenade.api.domain.repository;

import com.estudaenade.api.domain.model.RespostaPratica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RespostaPraticaRepository extends JpaRepository<RespostaPratica, UUID> {

}
