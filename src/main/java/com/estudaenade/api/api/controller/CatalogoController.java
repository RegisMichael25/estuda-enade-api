package com.estudaenade.api.api.controller;

import com.estudaenade.api.api.contrato.CatalogoApi;
import com.estudaenade.api.api.contrato.dto.NomeRequest;
import com.estudaenade.api.api.contrato.dto.TemaRequest;
import com.estudaenade.api.api.dto.ItemResponse;
import com.estudaenade.api.api.dto.UnidadeTempoResponse;
import com.estudaenade.api.domain.model.Curso;
import com.estudaenade.api.domain.model.Tema;
import com.estudaenade.api.domain.repository.CursoRepository;
import com.estudaenade.api.domain.repository.TemaRepository;
import com.estudaenade.api.domain.repository.UnidadeTempoRepository;
import com.estudaenade.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Cursos e temas usados nos filtros do banco e na leitura de desempenho, e as unidades de tempo da
 * duração das atividades. Rotas em {@link CatalogoApi}.
 */
@RestController
@RequiredArgsConstructor
public class CatalogoController implements CatalogoApi {

    private final CursoRepository cursoRepository;
    private final TemaRepository temaRepository;
    private final UnidadeTempoRepository unidadeTempoRepository;

    @Override
    public List<ItemResponse> cursos() {
        return cursoRepository.findAllByOrderByNomeAsc().stream().map(ItemResponse::de).toList();
    }

    /** Sem {@code cursoId}, todos os temas; com ele, só os temas desse curso. */
    @Override
    public List<ItemResponse> temas(UUID cursoId) {
        List<Tema> temas = cursoId == null
                ? temaRepository.findAllByOrderByNomeAsc()
                : temaRepository.findAllByCursoIdOrderByNomeAsc(cursoId);
        return temas.stream().map(ItemResponse::de).toList();
    }

    /** Linhas fixas da V00009: Minutos e Horas, nesta ordem. */
    @Override
    public List<UnidadeTempoResponse> unidadesTempo() {
        return unidadeTempoRepository.findAllByOrderByIdAsc().stream().map(UnidadeTempoResponse::de).toList();
    }

    /** Cria o curso ou devolve o existente com o mesmo nome. */
    @Override
    public ItemResponse criarCurso(NomeRequest req) {
        String nome = req.getNome().trim();
        return ItemResponse.de(cursoRepository.findByNomeIgnoreCase(nome)
                .orElseGet(() -> cursoRepository.save(Curso.builder().nome(nome).build())));
    }

    /** Cria o tema no curso ou devolve o existente com o mesmo nome nesse curso. */
    @Override
    public ItemResponse criarTema(TemaRequest req) {
        String nome = req.getNome().trim();
        Curso curso = cursoRepository.findById(req.getCursoId())
                .orElseThrow(() -> ApiException.naoEncontrado("Curso não encontrado."));
        return ItemResponse.de(temaRepository.findByCursoIdAndNomeIgnoreCase(curso.getId(), nome)
                .orElseGet(() -> temaRepository.save(Tema.builder().nome(nome).curso(curso).build())));
    }
}
