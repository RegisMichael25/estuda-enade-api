-- Mural de comentários de uma atividade (lista ou simulado), aberto à turma.
-- O aluno só vê e comenta depois de enviar a primeira tentativa; o professor da turma vê sempre.

create table comentario_atividade (
    id           uuid primary key,
    atividade_id uuid          not null references atividade (id) on delete cascade,
    autor_id     uuid          not null references usuario (id),
    texto        varchar(2000) not null,
    criado_em    timestamptz   not null,
    editado_em   timestamptz
);

create index idx_comentario_atividade_mural on comentario_atividade (atividade_id, criado_em);
