package com.estudaenade.api.api.controller;

import com.estudaenade.api.api.contrato.CatalogoApi;
import com.estudaenade.api.api.contrato.dto.NomeRequest;
import com.estudaenade.api.api.dto.ItemResponse;
import com.estudaenade.api.domain.model.Curso;
import com.estudaenade.api.domain.model.Tema;
import com.estudaenade.api.domain.repository.CursoRepository;
import com.estudaenade.api.domain.repository.TemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Cursos e temas usados nos filtros do banco e na leitura de desempenho. Rotas em {@link CatalogoApi}. */
@RestController
@RequiredArgsConstructor
public class CatalogoController implements CatalogoApi {

    private final CursoRepository cursoRepository;
    private final TemaRepository temaRepository;

    @Override
    public List<ItemResponse> cursos() {
        return cursoRepository.findAllByOrderByNomeAsc().stream().map(ItemResponse::de).toList();
    }

    @Override
    public List<ItemResponse> temas() {
        return temaRepository.findAllByOrderByNomeAsc().stream().map(ItemResponse::de).toList();
    }

    /** Cria o curso ou devolve o existente com o mesmo nome. */
    @Override
    public ItemResponse criarCurso(NomeRequest req) {
        String nome = req.getNome().trim();
        return ItemResponse.de(cursoRepository.findByNomeIgnoreCase(nome)
                .orElseGet(() -> cursoRepository.save(Curso.builder().nome(nome).build())));
    }

    /** Cria o tema ou devolve o existente com o mesmo nome. */
    @Override
    public ItemResponse criarTema(NomeRequest req) {
        String nome = req.getNome().trim();
        return ItemResponse.de(temaRepository.findByNomeIgnoreCase(nome)
                .orElseGet(() -> temaRepository.save(Tema.builder().nome(nome).build())));
    }
}
