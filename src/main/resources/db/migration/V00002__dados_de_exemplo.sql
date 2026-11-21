-- Dados de exemplo, espelhando o protótipo.

insert into usuario (id, nome, email, papel, criado_em) values
  ('00000000-0000-0000-0000-000000000001', 'Prof. Silva', 'professor@estudaenade.dev', 'PROFESSOR', now()),
  ('00000000-0000-0000-0000-000000000002', 'Prof. Lima',  'lima@estudaenade.dev',      'PROFESSOR', now()),
  ('00000000-0000-0000-0000-000000000011', 'Ana Souza',   'ana@estudaenade.dev',       'ALUNO',     now()),
  ('00000000-0000-0000-0000-000000000012', 'Bruno Lima',  'bruno@estudaenade.dev',     'ALUNO',     now());

insert into curso (id, nome) values
  ('00000000-0000-0000-0001-000000000001', 'Análise e Desenvolvimento de Sistemas');

insert into tema (id, nome) values
  ('00000000-0000-0000-0002-000000000001', 'Banco de Dados'),
  ('00000000-0000-0000-0002-000000000002', 'Engenharia de Software'),
  ('00000000-0000-0000-0002-000000000003', 'Redes'),
  ('00000000-0000-0000-0002-000000000004', 'Algoritmos');

insert into turma (id, nome, periodo, curso_id, professor_id, codigo_convite, criada_em) values
  ('00000000-0000-0000-0003-000000000001', 'ADS · Turma A', '2026.2',
   '00000000-0000-0000-0001-000000000001', '00000000-0000-0000-0000-000000000001', 'ADS-7K2', now());

insert into participacao (id, turma_id, aluno_id, entrou_em) values
  ('00000000-0000-0000-0004-000000000001', '00000000-0000-0000-0003-000000000001', '00000000-0000-0000-0000-000000000011', now()),
  ('00000000-0000-0000-0004-000000000002', '00000000-0000-0000-0003-000000000001', '00000000-0000-0000-0000-000000000012', now());

insert into questao (id, autor_id, enunciado, explicacao, ano, curso_id, tema_id, status, criada_em, atualizada_em, publicada_em) values
  ('00000000-0000-0000-0005-000000000001', '00000000-0000-0000-0000-000000000001',
   'Em um banco de dados relacional, qual forma normal elimina dependências transitivas entre atributos não-chave?',
   'A 3FN exige que nenhum atributo não-chave dependa de outro atributo não-chave.',
   2021, '00000000-0000-0000-0001-000000000001', '00000000-0000-0000-0002-000000000001', 'PUBLICADA', now(), now(), now()),
  ('00000000-0000-0000-0005-000000000002', '00000000-0000-0000-0000-000000000002',
   'No Scrum, quem é responsável por priorizar o backlog do produto?',
   'O Product Owner é o responsável por ordenar o Product Backlog de modo a maximizar o valor entregue.',
   2024, '00000000-0000-0000-0001-000000000001', '00000000-0000-0000-0002-000000000002', 'PUBLICADA', now(), now(), now()),
  ('00000000-0000-0000-0005-000000000003', '00000000-0000-0000-0000-000000000001',
   'Em qual camada do modelo OSI atua o protocolo TCP?',
   'O TCP é um protocolo da camada de transporte (camada 4), responsável pela entrega confiável entre processos.',
   2021, '00000000-0000-0000-0001-000000000001', '00000000-0000-0000-0002-000000000003', 'PUBLICADA', now(), now(), now()),
  ('00000000-0000-0000-0005-000000000004', '00000000-0000-0000-0000-000000000002',
   'Qual a complexidade de tempo da busca binária em um vetor ordenado?',
   'A cada passo a busca binária descarta metade do vetor, o que resulta em O(log n).',
   2024, '00000000-0000-0000-0001-000000000001', '00000000-0000-0000-0002-000000000004', 'PUBLICADA', now(), now(), now()),
  ('00000000-0000-0000-0005-000000000005', '00000000-0000-0000-0000-000000000001',
   'Qual comando SQL é usado para remover todas as linhas de uma tabela mantendo sua estrutura?',
   null,
   2024, '00000000-0000-0000-0001-000000000001', '00000000-0000-0000-0002-000000000001', 'RASCUNHO', now(), now(), null);

insert into alternativa (id, questao_id, ordem, texto, correta)
select gen_random_uuid(), q.id::uuid, a.ordem, a.texto, a.correta
from (values
  ('00000000-0000-0000-0005-000000000001', 0, 'Primeira forma normal (1FN)', false),
  ('00000000-0000-0000-0005-000000000001', 1, 'Segunda forma normal (2FN)', false),
  ('00000000-0000-0000-0005-000000000001', 2, 'Terceira forma normal (3FN)', true),
  ('00000000-0000-0000-0005-000000000001', 3, 'Forma normal de Boyce-Codd', false),
  ('00000000-0000-0000-0005-000000000001', 4, 'Quarta forma normal (4FN)', false),
  ('00000000-0000-0000-0005-000000000002', 0, 'Scrum Master', false),
  ('00000000-0000-0000-0005-000000000002', 1, 'Product Owner', true),
  ('00000000-0000-0000-0005-000000000002', 2, 'Time de desenvolvimento', false),
  ('00000000-0000-0000-0005-000000000002', 3, 'Stakeholders', false),
  ('00000000-0000-0000-0005-000000000002', 4, 'Gerente de projeto', false),
  ('00000000-0000-0000-0005-000000000003', 0, 'Física', false),
  ('00000000-0000-0000-0005-000000000003', 1, 'Enlace', false),
  ('00000000-0000-0000-0005-000000000003', 2, 'Rede', false),
  ('00000000-0000-0000-0005-000000000003', 3, 'Transporte', true),
  ('00000000-0000-0000-0005-000000000003', 4, 'Aplicação', false),
  ('00000000-0000-0000-0005-000000000004', 0, 'O(1)', false),
  ('00000000-0000-0000-0005-000000000004', 1, 'O(log n)', true),
  ('00000000-0000-0000-0005-000000000004', 2, 'O(n)', false),
  ('00000000-0000-0000-0005-000000000004', 3, 'O(n log n)', false),
  ('00000000-0000-0000-0005-000000000004', 4, 'O(n²)', false),
  ('00000000-0000-0000-0005-000000000005', 0, 'DROP TABLE', false),
  ('00000000-0000-0000-0005-000000000005', 1, 'TRUNCATE TABLE', false),
  ('00000000-0000-0000-0005-000000000005', 2, 'ALTER TABLE', false),
  ('00000000-0000-0000-0005-000000000005', 3, 'REVOKE', false)
) as a(questao_id, ordem, texto, correta)
join questao q on q.id = a.questao_id::uuid;

insert into atividade (id, turma_id, titulo, tipo, duracao_minutos, max_tentativas, status, criada_em, publicada_em) values
  ('00000000-0000-0000-0006-000000000001', '00000000-0000-0000-0003-000000000001', 'Simulado 1', 'SIMULADO', 30, 1, 'PUBLICADA', now(), now()),
  ('00000000-0000-0000-0006-000000000002', '00000000-0000-0000-0003-000000000001', 'Lista: Banco de Dados', 'LISTA', null, null, 'PUBLICADA', now(), now()),
  ('00000000-0000-0000-0006-000000000003', '00000000-0000-0000-0003-000000000001', 'Simulado 2', 'SIMULADO', 60, 2, 'RASCUNHO', now(), null);

insert into atividade_questao (id, atividade_id, questao_id, ordem) values
  (gen_random_uuid(), '00000000-0000-0000-0006-000000000001', '00000000-0000-0000-0005-000000000001', 0),
  (gen_random_uuid(), '00000000-0000-0000-0006-000000000001', '00000000-0000-0000-0005-000000000002', 1),
  (gen_random_uuid(), '00000000-0000-0000-0006-000000000001', '00000000-0000-0000-0005-000000000003', 2),
  (gen_random_uuid(), '00000000-0000-0000-0006-000000000001', '00000000-0000-0000-0005-000000000004', 3),
  (gen_random_uuid(), '00000000-0000-0000-0006-000000000002', '00000000-0000-0000-0005-000000000001', 0),
  (gen_random_uuid(), '00000000-0000-0000-0006-000000000003', '00000000-0000-0000-0005-000000000003', 0),
  (gen_random_uuid(), '00000000-0000-0000-0006-000000000003', '00000000-0000-0000-0005-000000000004', 1);
