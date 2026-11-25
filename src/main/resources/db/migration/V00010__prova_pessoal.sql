-- Prova que o aluno monta na prática livre. É só dele: sem vínculo com turma ou atividade.
create table prova_pessoal (
    id           uuid primary key,
    aluno_id     uuid         not null references usuario (id),
    curso_id     uuid         not null references curso (id),
    nome         varchar(80)  not null,
    criada_em    timestamptz  not null,
    concluida_em timestamptz                -- nulo = em andamento
);
create index idx_prova_pessoal_aluno on prova_pessoal (aluno_id, criada_em desc);

create table prova_pessoal_questao (
    id             uuid primary key,
    prova_id       uuid    not null references prova_pessoal (id) on delete cascade,
    questao_id     uuid    not null references questao (id),
    ordem          integer not null,
    alternativa_id uuid references alternativa (id) on delete set null, -- nulo = ainda sem resposta
    correta        boolean,                                             -- nulo = ainda sem resposta
    respondida_em  timestamptz,
    constraint uk_prova_pessoal_questao unique (prova_id, questao_id)
);
