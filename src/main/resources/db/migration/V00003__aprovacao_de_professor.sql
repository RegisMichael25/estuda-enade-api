-- Cadastro de professor com aprovação.
-- O professor entra aprovado quando usa um convite válido ou tem e-mail de domínio institucional;
-- nos demais casos fica pendente até um professor já aprovado liberar.

alter table usuario add column aprovado boolean not null default false;
alter table usuario add column aprovado_em timestamptz;
alter table usuario add column aprovado_por_id uuid references usuario (id);

-- Contas que já existiam (inclusive as de exemplo) continuam com acesso.
update usuario set aprovado = true;

-- Convite de uso único criado por um professor aprovado. Guarda só o hash do código.
create table convite_professor (
    id            uuid primary key,
    codigo_hash   varchar(64) not null unique,
    criado_por_id uuid        not null references usuario (id),
    criado_em     timestamptz not null,
    expira_em     timestamptz not null,
    usado_por_id  uuid references usuario (id),
    usado_em      timestamptz
);
create index idx_convite_professor_criador on convite_professor (criado_por_id);
create index idx_usuario_pendente on usuario (papel, criado_em) where aprovado = false;
