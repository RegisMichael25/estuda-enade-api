package com.estudaenade.api.domain.service;

import com.estudaenade.api.api.contrato.dto.AtividadeRequest;
import com.estudaenade.api.api.dto.AtividadeDtos.AtividadeDetalheResponse;
import com.estudaenade.api.api.dto.AtividadeDtos.AtividadeResponse;
import com.estudaenade.api.api.dto.QuestaoDtos.QuestaoResponse;
import com.estudaenade.api.domain.enums.StatusAtividade;
import com.estudaenade.api.domain.enums.StatusQuestao;
import com.estudaenade.api.domain.model.Atividade;
import com.estudaenade.api.domain.model.AtividadeQuestao;
import com.estudaenade.api.domain.model.Questao;
import com.estudaenade.api.domain.model.Turma;
import com.estudaenade.api.domain.repository.AtividadeRepository;
import com.estudaenade.api.domain.repository.QuestaoRepository;
import com.estudaenade.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Listas e simulados: montagem e publicação pelo professor. */
@Service
@RequiredArgsConstructor
@Transactional
public class AtividadeService {

    private final AtividadeRepository atividadeRepository;
    private final QuestaoRepository questaoRepository;
    private final TurmaService turmaService;

    public AtividadeResponse criar(UUID professorId, UUID turmaId, AtividadeRequest req) {
        Turma turma = turmaService.doProfessor(turmaId, professorId);
        Atividade atividade = Atividade.builder().turma(turma).titulo(req.getTitulo().trim()).tipo(req.getTipo()).build();
        aplicar(atividade, req);
        return AtividadeResponse.de(atividadeRepository.save(atividade));
    }

    /** Só rascunhos podem ser alterados: depois de publicada, a atividade fica igual para todos os alunos. */
    public AtividadeResponse atualizar(UUID professorId, UUID atividadeId, AtividadeRequest req) {
        Atividade atividade = doProfessor(atividadeId, professorId);
        exigirRascunho(atividade);
        // Remove as questões antigas antes de inserir as novas (restrição única atividade + questão)
        atividade.getQuestoes().clear();
        atividadeRepository.flush();
        aplicar(atividade, req);
        return AtividadeResponse.de(atividade);
    }

    public AtividadeResponse publicar(UUID professorId, UUID atividadeId) {
        Atividade atividade = doProfessor(atividadeId, professorId);
        exigirRascunho(atividade);
        if (atividade.getQuestoes().isEmpty()) {
            throw ApiException.regra("ATIVIDADE_SEM_QUESTOES", "Adicione ao menos uma questão antes de publicar.");
        }
        atividade.setStatus(StatusAtividade.PUBLICADA);
        atividade.setPublicadaEm(Instant.now());
        return AtividadeResponse.de(atividade);
    }

    /** Fecha a atividade para novas tentativas. Tentativas em andamento seguem até o envio ou o fim do tempo. */
    public AtividadeResponse encerrar(UUID professorId, UUID atividadeId) {
        Atividade atividade = doProfessor(atividadeId, professorId);
        if (atividade.getStatus() != StatusAtividade.PUBLICADA) {
            throw ApiException.regra("ATIVIDADE_NAO_PUBLICADA", "Só é possível encerrar uma atividade publicada.");
        }
        atividade.setStatus(StatusAtividade.ENCERRADA);
        return AtividadeResponse.de(atividade);
    }

    public void excluirRascunho(UUID professorId, UUID atividadeId) {
        Atividade atividade = doProfessor(atividadeId, professorId);
        exigirRascunho(atividade);
        atividadeRepository.delete(atividade);
    }

    @Transactional(readOnly = true)
    public List<AtividadeResponse> listarDaTurma(UUID professorId, UUID turmaId) {
        turmaService.doProfessor(turmaId, professorId);
        return atividadeRepository.findByTurmaIdOrderByCriadaEmDesc(turmaId).stream().map(AtividadeResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public AtividadeDetalheResponse detalhar(UUID professorId, UUID atividadeId) {
        Atividade atividade = doProfessor(atividadeId, professorId);
        List<QuestaoResponse> questoes = atividade.getQuestoes().stream()
                .map(aq -> QuestaoResponse.de(aq.getQuestao(), professorId)).toList();
        return new AtividadeDetalheResponse(AtividadeResponse.de(atividade), questoes);
    }

    /** Atividade de uma turma do professor, ou 404. */
    public Atividade doProfessor(UUID atividadeId, UUID professorId) {
        return atividadeRepository.findById(atividadeId)
                .filter(a -> a.getTurma().getProfessor().getId().equals(professorId))
                .orElseThrow(() -> ApiException.naoEncontrado("Atividade não encontrada."));
    }

    private void exigirRascunho(Atividade atividade) {
        if (atividade.getStatus() != StatusAtividade.RASCUNHO) {
            throw ApiException.regra("ATIVIDADE_JA_PUBLICADA", "A atividade já foi publicada e não pode mais ser alterada.");
        }
    }

    private void aplicar(Atividade atividade, AtividadeRequest req) {
        if (new HashSet<>(req.getQuestaoIds()).size() != req.getQuestaoIds().size()) {
            throw ApiException.regra("QUESTAO_REPETIDA", "A mesma questão foi incluída mais de uma vez.");
        }
        atividade.setTitulo(req.getTitulo().trim());
        atividade.setTipo(req.getTipo());
        atividade.setDuracaoMinutos(req.getDuracaoMinutos());
        atividade.setMaxTentativas(req.getMaxTentativas());

        int ordem = 0;
        for (UUID questaoId : req.getQuestaoIds()) {
            Questao questao = Optional.ofNullable(questaoId).flatMap(questaoRepository::findById)
                    .filter(q -> q.getStatus() == StatusQuestao.PUBLICADA)
                    .orElseThrow(() -> ApiException.regra("QUESTAO_INDISPONIVEL",
                            "Só questões publicadas podem entrar em uma atividade."));
            atividade.getQuestoes().add(AtividadeQuestao.builder()
                    .atividade(atividade).questao(questao).ordem(ordem++).build());
        }
    }
}
