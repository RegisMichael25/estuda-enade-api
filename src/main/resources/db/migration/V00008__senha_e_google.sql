-- Login com senha e com Google. O código por e-mail passa a servir só para redefinir a senha.

alter table usuario add column senha_hash        varchar(100);
alter table usuario add column senha_alterada_em timestamptz;
alter table usuario add column google_sub        varchar(255) unique;
-- Verdadeiro quando o dono do e-mail foi comprovado (Google ou código por e-mail).
-- A aprovação de professor pelo domínio institucional só vale com o e-mail comprovado.
alter table usuario add column email_verificado  boolean not null default false;
-- Bloqueio temporário depois de várias senhas erradas seguidas.
alter table usuario add column falhas_login      integer not null default 0;
alter table usuario add column bloqueado_ate     timestamptz;

-- Todas as contas existentes entraram com o código por e-mail: o e-mail delas está comprovado.
update usuario set email_verificado = true;

-- Contas de exemplo (V00002): senha "123456", gerada com o PasswordEncoder da aplicação.
-- Ela não segue a política de senha de propósito, por ser gravada direto aqui.
update usuario
   set senha_hash = '{bcrypt}$2a$10$ov72f0WouE7uX7/HBJgGD.To1rYsOQP.2B/mI0INrbQha4m1sV38y',
       senha_alterada_em = now()
 where id in ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002',
              '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000012')
   and senha_hash is null;
