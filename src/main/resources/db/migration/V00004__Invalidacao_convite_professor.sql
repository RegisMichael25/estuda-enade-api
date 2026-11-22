-- Permite invalidar manualmente um convite ativo antes da expiração.
alter table convite_professor add column invalidado_em timestamptz;