-- Unidade em que o professor informa a duração da atividade. A tabela funciona como um enum:
-- as linhas são fixas, criadas aqui, e o código as reconhece pelo campo codigo.
create table unidade_tempo (
    id            uuid         primary key,
    nome          varchar(40)  not null,
    codigo        varchar(10)  not null unique,
    descricao     varchar(255),
    criada_em     timestamptz  not null,
    atualizada_em timestamptz  not null
);

insert into unidade_tempo (id, nome, codigo, descricao, criada_em, atualizada_em) values
  ('00000000-0000-0000-0007-000000000001', 'Minutos', 'min', 'Duração informada em minutos.', now(), now()),
  ('00000000-0000-0000-0007-000000000002', 'Horas',   'h',   'Duração informada em horas.',   now(), now());

-- O valor passa a ser o que o professor digitou, na unidade escolhida.
alter table atividade rename column duracao_minutos to duracao;
alter table atividade add column unidade_tempo_id uuid references unidade_tempo (id);

-- As durações existentes estavam em minutos.
update atividade
   set unidade_tempo_id = '00000000-0000-0000-0007-000000000001'
 where duracao is not null;

-- Com duração, a unidade é obrigatória; sem duração (atividade sem tempo), as duas ficam nulas.
alter table atividade add constraint ck_atividade_duracao_unidade
    check ((duracao is null) = (unidade_tempo_id is null));
