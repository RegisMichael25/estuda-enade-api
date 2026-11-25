package com.estudaenade.api.domain.service;

import com.estudaenade.api.api.dto.QuestaoDtos.ContagemTemaResponse;
import com.estudaenade.api.api.dto.QuestaoDtos.GabaritoResponse;
import com.estudaenade.api.domain.enums.HistoricoPratica;
import com.estudaenade.api.domain.model.Alternativa;
import com.estudaenade.api.domain.model.Questao;
import com.estudaenade.api.domain.model.RespostaPratica;
import com.estudaenade.api.domain.repository.RespostaPraticaRepository;
import com.estudaenade.api.domain.repository.UsuarioRepository;
import com.estudaenade.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Prática livre: o aluno responde uma questão do banco e recebe gabarito e explicação na hora. */
@Service
@RequiredArgsConstructor
public class PraticaService {

    private final QuestaoService questaoService;
    private final RespostaPraticaRepository respostaPraticaRepository;
    private final UsuarioRepository usuarioRepository;
    private final DesempenhoService desempenhoService;

    /** Questões disponíveis por tema, com o desempenho do aluno em cada um (para destacar os temas fracos). */
    @Transactional(readOnly = true)
    public List<ContagemTemaResponse> contagem(UUID alunoId, UUID cursoId, List<Integer> anos, HistoricoPratica historico) {
        Map<UUID, long[]> desempenho = new HashMap<>();
        desempenhoService.doAluno(alunoId).porTema()
                .forEach(t -> desempenho.put(t.temaId(), new long[]{t.respondidas(), t.acertos()}));
        return questaoService.contagemDaPratica(alunoId, cursoId, anos, historico, desempenho);
    }

    @Transactional
    public GabaritoResponse responder(UUID alunoId, UUID questaoId, UUID alternativaId) {
        if (alternativaId == null) {
            throw ApiException.regra("ALTERNATIVA_OBRIGATORIA", "Escolha uma alternativa.");
        }
        Questao questao = questaoService.disponivelParaPratica(alunoId, questaoId);
        Alternativa marcada = questao.getAlternativas().stream()
                .filter(a -> a.getId().equals(alternativaId)).findFirst()
                .orElseThrow(() -> ApiException.regra("ALTERNATIVA_INVALIDA", "A alternativa não pertence a esta questão."));
        Alternativa correta = questao.alternativaCorreta();

        respostaPraticaRepository.save(RespostaPratica.builder()
                .aluno(usuarioRepository.getReferenceById(alunoId))
                .questao(questao).alternativa(marcada).correta(marcada.isCorreta())
                .build());

        return new GabaritoResponse(questao.getId(), correta.getId(), correta.letra(), questao.getExplicacao(),
                marcada.getId(), marcada.isCorreta());
    }
}
