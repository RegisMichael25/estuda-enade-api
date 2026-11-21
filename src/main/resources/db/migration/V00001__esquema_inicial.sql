-- Estuda ENADE — esquema inicial

-- ---------- contas e acesso ----------
create table usuario (
    id        uuid primary key,
    nome      varchar(120) not null,
    email     varchar(254) not null unique,
    papel     varchar(20)  not null check (papel in ('ALUNO', 'PROFESSOR')),
    criado_em timestamptz  not null
);

-- Código de uso único enviado por e-mail. Guarda só o hash do código.
create table codigo_acesso (
    id          uuid primary key,
    email       varchar(254) not null,
    codigo_hash varchar(64)  not null,
    expira_em   timestamptz  not null,
    usado       boolean      not null default false,
    tentativas  integer      not null default 0,
    criado_em   timestamptz  not null
);
create index idx_codigo_acesso_email on codigo_acesso (email, criado_em);

create table refresh_token (
    id         uuid primary key,
    usuario_id uuid        not null references usuario (id) on delete cascade,
    token_hash varchar(64) not null unique,
    expira_em  timestamptz not null,
    revogado   boolean     not null default false,
    criado_em  timestamptz not null
);

-- ---------- catálogo ----------
create table curso (
    id   uuid primary key,
    nome varchar(120) not null
);
create unique index uk_curso_nome on curso (lower(nome));

create table tema (
    id   uuid primary key,
    nome varchar(120) not null
);
create unique index uk_tema_nome on tema (lower(nome));

-- ---------- turmas ----------
create table turma (
    id             uuid primary key,
    nome           varchar(120) not null,
    periodo        varchar(20),
    curso_id       uuid references curso (id),
    professor_id   uuid        not null references usuario (id),
    codigo_convite varchar(12) not null unique,
    criada_em      timestamptz not null
);
create index idx_turma_professor on turma (professor_id);

create table participacao (
    id        uuid primary key,
    turma_id  uuid        not null references turma (id) on delete cascade,
    aluno_id  uuid        not null references usuario (id),
    entrou_em timestamptz not null,
    constraint uk_participacao unique (turma_id, aluno_id)
);
create index idx_participacao_aluno on participacao (aluno_id);

-- ---------- banco de questões ----------
create table questao (
    id            uuid primary key,
    autor_id      uuid        not null references usuario (id),
    enunciado     text        not null,
    explicacao    text,
    ano           integer     not null,
    curso_id      uuid        not null references curso (id),
    tema_id       uuid        not null references tema (id),
    status        varchar(20) not null check (status in ('RASCUNHO', 'PUBLICADA', 'OCULTA')),
    criada_em     timestamptz not null,
    atualizada_em timestamptz not null,
    publicada_em  timestamptz
);
create index idx_questao_filtros on questao (status, curso_id, tema_id, ano);
create index idx_questao_autor on questao (autor_id);

create table alternativa (
    id         uuid primary key,
    questao_id uuid    not null references questao (id) on delete cascade,
    ordem      integer not null,
    texto      text    not null,
    correta    boolean not null default false,
    constraint uk_alternativa_ordem unique (questao_id, ordem)
);

-- Sinalização de possível erro: marca para revisão, não remove a questão.
create table sinalizacao (
    id           uuid primary key,
    questao_id   uuid        not null references questao (id) on delete cascade,
    usuario_id   uuid        not null references usuario (id),
    motivo       text,
    resolvida    boolean     not null default false,
    criada_em    timestamptz not null,
    resolvida_em timestamptz
);
create index idx_sinalizacao_questao on sinalizacao (questao_id, resolvida);

-- ---------- atividades (listas e simulados) ----------
create table atividade (
    id               uuid primary key,
    turma_id         uuid         not null references turma (id) on delete cascade,
    titulo           varchar(160) not null,
    tipo             varchar(20)  not null check (tipo in ('LISTA', 'SIMULADO')),
    duracao_minutos  integer,     -- nulo = sem tempo
    max_tentativas   integer,     -- nulo = tentativas ilimitadas
    status           varchar(20)  not null check (status in ('RASCUNHO', 'PUBLICADA', 'ENCERRADA')),
    criada_em        timestamptz  not null,
    publicada_em     timestamptz
);
create index idx_atividade_turma on atividade (turma_id, status);

create table atividade_questao (
    id           uuid primary key,
    atividade_id uuid    not null references atividade (id) on delete cascade,
    questao_id   uuid    not null references questao (id),
    ordem        integer not null,
    constraint uk_atividade_questao unique (atividade_id, questao_id)
);

-- ---------- tentativas e respostas ----------
create table tentativa (
    id            uuid primary key,
    atividade_id  uuid        not null references atividade (id) on delete cascade,
    aluno_id      uuid        not null references usuario (id),
    numero        integer     not null,
    iniciada_em   timestamptz not null,
    expira_em     timestamptz,           -- nulo = atividade sem tempo
    finalizada_em timestamptz,           -- nulo = em andamento
    encerramento  varchar(20) check (encerramento in ('ENVIO', 'TEMPO')),
    acertos       integer,
    total         integer     not null,
    constraint uk_tentativa_numero unique (atividade_id, aluno_id, numero)
);
-- No máximo uma tentativa em andamento por aluno em cada atividade
create unique index uk_tentativa_aberta on tentativa (atividade_id, aluno_id) where finalizada_em is null;
create index idx_tentativa_aluno on tentativa (aluno_id);
create index idx_tentativa_expiracao on tentativa (expira_em) where finalizada_em is null;

create table resposta_tentativa (
    id             uuid primary key,
    tentativa_id   uuid        not null references tentativa (id) on delete cascade,
    questao_id     uuid        not null references questao (id),
    alternativa_id uuid references alternativa (id) on delete set null, -- nulo = em branco
    correta        boolean,                                             -- preenchido ao finalizar
    respondida_em  timestamptz not null,
    constraint uk_resposta_tentativa unique (tentativa_id, questao_id)
);

create table resposta_pratica (
    id             uuid primary key,
    aluno_id       uuid        not null references usuario (id),
    questao_id     uuid        not null references questao (id),
    alternativa_id uuid references alternativa (id) on delete set null,
    correta        boolean     not null,
    respondida_em  timestamptz not null
);
create index idx_resposta_pratica_aluno on resposta_pratica (aluno_id);

-- Toda resposta já corrigida do aluno: prática livre + tentativas finalizadas.
create view resposta_avaliada as
select rp.aluno_id, rp.questao_id, rp.correta, rp.respondida_em, null::uuid as tentativa_id
from resposta_pratica rp
union all
select t.aluno_id, rt.questao_id, coalesce(rt.correta, false), t.finalizada_em, t.id
from resposta_tentativa rt
join tentativa t on t.id = rt.tentativa_id
where t.finalizada_em is not null;
