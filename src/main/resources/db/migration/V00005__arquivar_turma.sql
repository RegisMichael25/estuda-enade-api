-- Turma arquivada: some da lista padrão do professor, não aceita novos alunos
-- e não recebe novas atividades. Alunos, atividades e resultados continuam guardados.

alter table turma add column arquivada_em timestamptz;
