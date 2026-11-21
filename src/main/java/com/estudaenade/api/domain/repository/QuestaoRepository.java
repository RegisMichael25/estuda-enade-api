package com.estudaenade.api.domain.repository;

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
}
