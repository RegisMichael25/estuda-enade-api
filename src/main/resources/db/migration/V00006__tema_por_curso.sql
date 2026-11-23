-- Cada tema passa a pertencer a um curso. O mesmo nome pode existir em cursos diferentes.
--
-- Os temas que já existem vão para o curso das questões que os usam. Um tema usado em
-- mais de um curso fica no primeiro (por nome) e ganha uma cópia em cada um dos outros,
-- com as questões desses cursos apontando para a cópia. Tema sem questão vai para o
-- primeiro curso; se não houver curso nenhum, é removido.

drop index uk_tema_nome;

alter table tema add column curso_id uuid references curso (id);

update tema t
set curso_id = (select q.curso_id
                from questao q
                join curso c on c.id = q.curso_id
                where q.tema_id = t.id
                order by c.nome, c.id
                limit 1);

with copias as (
    select distinct q.tema_id, q.curso_id, gen_random_uuid() as novo_id
    from questao q
    join tema t on t.id = q.tema_id
    where q.curso_id <> t.curso_id
), inseridos as (
    insert into tema (id, nome, curso_id)
    select c.novo_id, t.nome, c.curso_id
    from copias c
    join tema t on t.id = c.tema_id
)
update questao q
set tema_id = c.novo_id
from copias c
where q.tema_id = c.tema_id
  and q.curso_id = c.curso_id;

update tema
set curso_id = (select id from curso order by nome, id limit 1)
where curso_id is null;

delete from tema where curso_id is null;

alter table tema alter column curso_id set not null;

create unique index uk_tema_curso_nome on tema (curso_id, lower(nome));
