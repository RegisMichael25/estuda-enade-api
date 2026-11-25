package com.estudaenade.api.domain.repository;

import com.estudaenade.api.domain.enums.StatusQuestao;
import com.estudaenade.api.domain.model.Questao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface QuestaoRepository extends JpaRepository<Questao, UUID>, JpaSpecificationExecutor<Questao> {

    /**
     * Questões que o aluno ainda vai responder (ou está respondendo) em uma atividade das suas turmas
     * e cujo gabarito ele ainda não viu: a atividade está aberta, ou ele tem tentativa em andamento,
     * e ele ainda não concluiu nenhuma tentativa dela. Essas questões ficam fora da prática livre.
     */
    @Query(value = """
            select distinct aq.questao_id
            from atividade_questao aq
            join atividade a on a.id = aq.atividade_id
            join participacao p on p.turma_id = a.turma_id and p.aluno_id = :alunoId
            where (a.status = 'PUBLICADA'
                   or exists (select 1 from tentativa t
                              where t.atividade_id = a.id and t.aluno_id = :alunoId and t.finalizada_em is null))
              and not exists (select 1 from tentativa t
                              where t.atividade_id = a.id and t.aluno_id = :alunoId and t.finalizada_em is not null)
            """, nativeQuery = true)
    List<UUID> reservadasParaAtividade(UUID alunoId);

    /** Questões que o aluno já respondeu e teve corrigidas (prática ou tentativa enviada). */
    @Query(value = "select distinct r.questao_id from resposta_avaliada r where r.aluno_id = :alunoId", nativeQuery = true)
    List<UUID> respondidasPeloAluno(UUID alunoId);

    /** Questões cuja última resposta corrigida do aluno foi errada. */
    @Query(value = """
            select u.questao_id from (
                select distinct on (r.questao_id) r.questao_id, r.correta
                from resposta_avaliada r
                where r.aluno_id = :alunoId
                order by r.questao_id, r.respondida_em desc
            ) u
            where not u.correta
            """, nativeQuery = true)
    List<UUID> erradasNaUltimaResposta(UUID alunoId);

    // Anos para os filtros, do mais recente ao mais antigo. Uma consulta com e outra sem curso,
    // para não depender de parâmetro nulo na query.

    @Query("select distinct q.ano from Questao q where q.status = :status order by q.ano desc")
    List<Integer> anosComStatus(StatusQuestao status);

    @Query("select distinct q.ano from Questao q where q.status = :status and q.curso.id = :cursoId order by q.ano desc")
    List<Integer> anosComStatusNoCurso(StatusQuestao status, UUID cursoId);

    /** Mesma visibilidade do banco do professor: publicadas de todos e as dele em qualquer estado. */
    @Query("""
            select distinct q.ano from Questao q
            where (q.status = :publicada or q.autor.id = :professorId)
            order by q.ano desc
            """)
    List<Integer> anosVisiveisAoProfessor(StatusQuestao publicada, UUID professorId);

    @Query("""
            select distinct q.ano from Questao q
            where (q.status = :publicada or q.autor.id = :professorId) and q.curso.id = :cursoId
            order by q.ano desc
            """)
    List<Integer> anosVisiveisAoProfessorNoCurso(StatusQuestao publicada, UUID professorId, UUID cursoId);
}
