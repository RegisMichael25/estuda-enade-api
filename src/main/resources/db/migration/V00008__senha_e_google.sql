-- Login com senha e com Google. O código por e-mail passa a servir só para redefinir a senha.

alter table usuario add column senha_hash        varchar(100);
alter table usuario add column senha_alterada_em timestamptz;
alter table usuario add column google_sub        varchar(255) unique;
alter table usuario add column email_verificado  boolean not null default false;
alter table usuario add column falhas_login      integer not null default 0;
alter table usuario add column bloqueado_ate     timestamptz;

-- As contas existentes entraram com código por e-mail.
update usuario set email_verificado = true;

-- Contas de exemplo (V00002): senha "123456".
update usuario
   set senha_hash = '{bcrypt}$2a$10$ov72f0WouE7uX7/HBJgGD.To1rYsOQP.2B/mI0INrbQha4m1sV38y',
       senha_alterada_em = now()
 where id in ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002',
              '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000012')
   and senha_hash is null;
