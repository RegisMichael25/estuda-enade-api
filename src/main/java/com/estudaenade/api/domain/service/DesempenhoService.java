package com.estudaenade.api.domain.service;

import com.estudaenade.api.api.dto.AtividadeDtos.AtividadeResponse;
import com.estudaenade.api.api.dto.AtividadeDtos.TentativaResumoResponse;
import com.estudaenade.api.api.dto.DesempenhoDtos.*;
import com.estudaenade.api.api.dto.ItemResponse;
import com.estudaenade.api.domain.model.Atividade;
import com.estudaenade.api.domain.model.Usuario;
import com.estudaenade.api.domain.repository.ParticipacaoRepository;
import com.estudaenade.api.domain.repository.TentativaRepository;
import com.estudaenade.api.domain.repository.UsuarioRepository;
import com.estudaenade.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Indicadores de aprendizagem por aluno, turma e tema (consultas agregadas em SQL). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DesempenhoService {

    private static final RowMapper<TemaDesempenho> TEMA = (rs, i) -> new TemaDesempenho(
            rs.getObject("id", UUID.class), rs.getString("nome"), rs.getLong("respondidas"), rs.getLong("acertos"));

    private static final String POR_TEMA = """
            select te.id, te.nome, count(*) as respondidas,
                   sum(case when r.correta then 1 else 0 end) as acertos
            from %s
            join questao q on q.id = r.questao_id
            join tema te on te.id = q.tema_id
            where %s
            group by te.id, te.nome
            order by te.nome
            """;

    private static final String RESPOSTAS_DE_TENTATIVAS =
            "resposta_tentativa r join tentativa t on t.id = r.tentativa_id join atividade a on a.id = t.atividade_id";

    private final JdbcTemplate jdbc;
    private final TurmaService turmaService;
    private final AtividadeService atividadeService;
    private final ParticipacaoRepository participacaoRepository;
    private final TentativaRepository tentativaRepository;
    private final UsuarioRepository usuarioRepository;

    // ======================== aluno ========================

    /** Tudo o que o aluno já respondeu e teve corrigido: prática livre + tentativas finalizadas. */
    public DesempenhoAlunoResponse doAluno(UUID alunoId) {
        List<TemaDesempenho> porTema = jdbc.query(
                POR_TEMA.formatted("resposta_avaliada r", "r.aluno_id = ?"), TEMA, alunoId);
        long respondidas = porTema.stream().mapToLong(TemaDesempenho::respondidas).sum();
        long acertos = porTema.stream().mapToLong(TemaDesempenho::acertos).sum();
        String melhorTema = porTema.stream().max(Comparator.comparingInt(TemaDesempenho::percentual))
                .map(TemaDesempenho::tema).orElse(null);
        return new DesempenhoAlunoResponse(respondidas, acertos,
                respondidas == 0 ? null : percentual(acertos, respondidas), melhorTema, porTema);
    }

    public List<TemaDesempenho> porTemaDaTentativa(UUID tentativaId) {
        return jdbc.query(POR_TEMA.formatted("resposta_tentativa r", "r.tentativa_id = ?"), TEMA, tentativaId);
    }

    // ======================== professor ========================

    /** Painel inicial: só turmas não arquivadas do professor. */
    public PainelProfessorResponse painelDoProfessor(UUID professorId) {
        List<PainelTurma> turmas = jdbc.query("""
                select t.id, t.nome, t.periodo, c.id as curso_id, c.nome as curso_nome,
                       (select count(*) from participacao p where p.turma_id = t.id) as total_alunos,
                       (select count(*) from atividade a where a.turma_id = t.id and a.status = 'PUBLICADA') as abertas,
                       (select round(100.0 * sum(te.acertos) / nullif(sum(te.total), 0))::int
                          from tentativa te join atividade a on a.id = te.atividade_id
                         where a.turma_id = t.id and te.finalizada_em is not null) as media
                from turma t
                left join curso c on c.id = t.curso_id
                where t.professor_id = ? and t.arquivada_em is null
                order by t.criada_em desc
                """, (rs, i) -> {
            UUID cursoId = rs.getObject("curso_id", UUID.class);
            return new PainelTurma(rs.getObject("id", UUID.class), rs.getString("nome"), rs.getString("periodo"),
                    cursoId == null ? null : new ItemResponse(cursoId, rs.getString("curso_nome")),
                    rs.getLong("total_alunos"), rs.getLong("abertas"), (Integer) rs.getObject("media"));
        }, professorId);

        long totalAlunos = contar("""
                select count(distinct p.aluno_id) from participacao p join turma t on t.id = p.turma_id
                where t.professor_id = ? and t.arquivada_em is null
                """, professorId);
        long atividadesAbertas = turmas.stream().mapToLong(PainelTurma::atividadesAbertas).sum();
        long questoesNoBanco = contar("select count(*) from questao where status = 'PUBLICADA'");

        List<AtividadeRecentePainel> atividades = jdbc.query("""
                select a.id, a.titulo, a.tipo, a.status, a.duracao_minutos, t.id as turma_id, t.nome as turma_nome,
                       (select count(*) from atividade_questao aq where aq.atividade_id = a.id) as total_questoes,
                       (select count(distinct te.aluno_id) from tentativa te
                         join participacao p on p.turma_id = a.turma_id and p.aluno_id = te.aluno_id
                         where te.atividade_id = a.id and te.finalizada_em is not null) as concluiram,
                       (select count(*) from participacao p where p.turma_id = t.id) as total_alunos
                from atividade a
                join turma t on t.id = a.turma_id
                where t.professor_id = ? and t.arquivada_em is null and a.status <> 'RASCUNHO'
                order by a.publicada_em desc
                limit 6
                """, (rs, i) -> new AtividadeRecentePainel(rs.getObject("id", UUID.class), rs.getString("titulo"),
                rs.getString("tipo"), rs.getString("status"), rs.getInt("total_questoes"),
                (Integer) rs.getObject("duracao_minutos"), rs.getLong("concluiram"),
                new ItemResponse(rs.getObject("turma_id", UUID.class), rs.getString("turma_nome")),
                rs.getLong("total_alunos")), professorId);

        return new PainelProfessorResponse(turmas.size(), totalAlunos, atividadesAbertas, questoesNoBanco,
                turmas, atividades);
    }

    public ResumoTurmaResponse resumoDaTurma(UUID professorId, UUID turmaId) {
        turmaService.doProfessor(turmaId, professorId);

        long questoesNoBanco = contar("select count(*) from questao where status = 'PUBLICADA'");
        long atividadesAbertas = contar("select count(*) from atividade where turma_id = ? and status = 'PUBLICADA'", turmaId);
        Integer media = jdbc.queryForObject("""
                select round(100.0 * sum(t.acertos) / nullif(sum(t.total), 0))::int
                from tentativa t join atividade a on a.id = t.atividade_id
                where a.turma_id = ? and t.finalizada_em is not null
                """, Integer.class, turmaId);

        List<AtividadeRecente> atividades = jdbc.query("""
                select a.id, a.titulo, a.tipo, a.status, a.duracao_minutos,
                       (select count(*) from atividade_questao aq where aq.atividade_id = a.id) as total_questoes,
                       (select count(distinct t.aluno_id) from tentativa t
                         join participacao p on p.turma_id = a.turma_id and p.aluno_id = t.aluno_id
                         where t.atividade_id = a.id and t.finalizada_em is not null) as concluiram
                from atividade a
                where a.turma_id = ? and a.status <> 'RASCUNHO'
                order by a.publicada_em desc
                limit 5
                """, (rs, i) -> new AtividadeRecente(rs.getObject("id", UUID.class), rs.getString("titulo"),
                rs.getString("tipo"), rs.getString("status"), rs.getInt("total_questoes"),
                (Integer) rs.getObject("duracao_minutos"), rs.getLong("concluiram")), turmaId);

        return new ResumoTurmaResponse(participacaoRepository.countByTurmaId(turmaId), questoesNoBanco,
                atividadesAbertas, media, atividades);
    }

    /** Resultado da turma em uma atividade. Com várias tentativas, vale a melhor de cada aluno. */
    public ResultadosAtividadeResponse resultadosDaAtividade(UUID professorId, UUID atividadeId) {
        Atividade atividade = atividadeService.doProfessor(atividadeId, professorId);
        UUID turmaId = atividade.getTurma().getId();

        List<ResultadoAluno> alunos = jdbc.query("""
                select u.id, u.nome,
                       (select count(*) from tentativa t where t.atividade_id = ? and t.aluno_id = u.id) as tentativas,
                       m.acertos, m.total,
                       extract(epoch from (m.finalizada_em - m.iniciada_em))::bigint as tempo_segundos
                from participacao p
                join usuario u on u.id = p.aluno_id
                left join lateral (
                    select t.acertos, t.total, t.iniciada_em, t.finalizada_em
                    from tentativa t
                    where t.atividade_id = ? and t.aluno_id = u.id and t.finalizada_em is not null
                    order by t.acertos desc, t.finalizada_em
                    limit 1
                ) m on true
                where p.turma_id = ?
                order by u.nome
                """, (rs, i) -> new ResultadoAluno(rs.getObject("id", UUID.class), rs.getString("nome"),
                rs.getLong("tentativas"), (Integer) rs.getObject("acertos"), (Integer) rs.getObject("total"),
                (Long) rs.getObject("tempo_segundos")), atividadeId, atividadeId, turmaId);

        List<ResultadoAluno> concluiram = alunos.stream().filter(a -> a.acertos() != null).toList();
        Double media = concluiram.isEmpty() ? null : arredondar(concluiram.stream()
                .mapToDouble(a -> a.total() == 0 ? 0 : 10.0 * a.acertos() / a.total()).average().orElse(0));
        Long tempoMedio = concluiram.isEmpty() ? null : Math.round(concluiram.stream()
                .map(ResultadoAluno::tempoSegundos).filter(Objects::nonNull).mapToLong(Long::longValue).average().orElse(0));

        List<TemaDesempenho> porTema = jdbc.query(POR_TEMA.formatted(RESPOSTAS_DE_TENTATIVAS,
                "t.atividade_id = ? and t.finalizada_em is not null"), TEMA, atividadeId);

        return new ResultadosAtividadeResponse(AtividadeResponse.de(atividade), alunos.size(), concluiram.size(),
                media, tempoMedio, porTema, alunos);
    }

    /** Desempenho de um aluno nas atividades da turma. A prática livre do aluno não aparece aqui. */
    public DesempenhoAlunoTurmaResponse doAlunoNaTurma(UUID professorId, UUID turmaId, UUID alunoId) {
        turmaService.doProfessor(turmaId, professorId);
        if (!participacaoRepository.existsByTurmaIdAndAlunoId(turmaId, alunoId)) {
            throw ApiException.naoEncontrado("Aluno não está nesta turma.");
        }
        Usuario aluno = usuarioRepository.findById(alunoId)
                .orElseThrow(() -> ApiException.naoEncontrado("Aluno não encontrado."));

        List<TemaDesempenho> porTema = jdbc.query(POR_TEMA.formatted(RESPOSTAS_DE_TENTATIVAS,
                "a.turma_id = ? and t.aluno_id = ? and t.finalizada_em is not null"), TEMA, turmaId, alunoId);
        List<TentativaResumoResponse> tentativas = tentativaRepository.findByAlunoIdOrderByIniciadaEmDesc(alunoId).stream()
                .filter(t -> t.getAtividade().getTurma().getId().equals(turmaId))
                .map(TentativaResumoResponse::de).toList();
        return new DesempenhoAlunoTurmaResponse(aluno.getId(), aluno.getNome(), porTema, tentativas);
    }

    // ======================== apoio ========================

    private long contar(String sql, Object... args) {
        Long total = jdbc.queryForObject(sql, Long.class, args);
        return total == null ? 0 : total;
    }

    private static int percentual(long acertos, long total) {
        return com.estudaenade.api.api.dto.DesempenhoDtos.percentual(acertos, total);
    }

    private static double arredondar(double valor) {
        return Math.round(valor * 10) / 10.0;
    }
}
