package com.estudaenade.api.api.dto;

import com.estudaenade.api.domain.enums.StatusQuestao;
import com.estudaenade.api.domain.model.Alternativa;
import com.estudaenade.api.domain.model.Questao;
import com.estudaenade.api.domain.model.Sinalizacao;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Respostas desta área. As requisições são geradas do contrato OpenAPI (pacote {@code api.contrato.dto}). */
public final class QuestaoDtos {

    private QuestaoDtos() {
    }

    // ---------- professor ----------

    public record AlternativaResponse(UUID id, String letra, String texto, boolean correta) {
        public static AlternativaResponse de(Alternativa a) {
            return new AlternativaResponse(a.getId(), a.letra(), a.getTexto(), a.isCorreta());
        }
    }

    /** Questão completa, com gabarito e explicação. Nunca devolver ao aluno antes de ele responder. */
    public record QuestaoResponse(UUID id, String enunciado, String explicacao, Integer ano,
                                  ItemResponse curso, ItemResponse tema, StatusQuestao status,
                                  ItemResponse autor, boolean minha, long sinalizacoesAbertas,
                                  List<AlternativaResponse> alternativas,
                                  Instant criadaEm, Instant atualizadaEm, Instant publicadaEm) {
        public static QuestaoResponse de(Questao q, UUID usuarioId) {
            return new QuestaoResponse(q.getId(), q.getEnunciado(), q.getExplicacao(), q.getAno(),
                    ItemResponse.de(q.getCurso()), ItemResponse.de(q.getTema()), q.getStatus(),
                    new ItemResponse(q.getAutor().getId(), q.getAutor().getNome()),
                    q.getAutor().getId().equals(usuarioId), q.getSinalizacoesAbertas(),
                    q.getAlternativas().stream().map(AlternativaResponse::de).toList(),
                    q.getCriadaEm(), q.getAtualizadaEm(), q.getPublicadaEm());
        }
    }

    /** Sem o autor: quem sinalizou não é identificado para os professores. */
    public record SinalizacaoResponse(UUID id, String motivo, boolean resolvida, Instant criadaEm) {
        public static SinalizacaoResponse de(Sinalizacao s) {
            return new SinalizacaoResponse(s.getId(), s.getMotivo(), s.isResolvida(), s.getCriadaEm());
        }
    }

    // ---------- aluno ----------

    public record AlternativaAlunoResponse(UUID id, String letra, String texto) {
        public static AlternativaAlunoResponse de(Alternativa a) {
            return new AlternativaAlunoResponse(a.getId(), a.letra(), a.getTexto());
        }
    }

    /** Questão para responder: sem gabarito e sem explicação. */
    public record QuestaoAlunoResponse(UUID id, String enunciado, Integer ano, ItemResponse curso, ItemResponse tema,
                                       List<AlternativaAlunoResponse> alternativas) {
        public static QuestaoAlunoResponse de(Questao q) {
            return new QuestaoAlunoResponse(q.getId(), q.getEnunciado(), q.getAno(),
                    ItemResponse.de(q.getCurso()), ItemResponse.de(q.getTema()),
                    q.getAlternativas().stream().map(AlternativaAlunoResponse::de).toList());
        }
    }

    /**
     * Tema, quantas questões dele estão disponíveis para a prática livre e o desempenho do aluno nele
     * (todas as respostas já corrigidas), para destacar os temas fracos.
     */
    public record ContagemTemaResponse(ItemResponse tema, long total, long respondidas, long acertos) {
    }

    /** Feedback de uma questão já respondida. */
    public record GabaritoResponse(UUID questaoId, UUID alternativaCorretaId, String letraCorreta, String explicacao,
                                   UUID alternativaMarcadaId, boolean acertou) {
    }
}
