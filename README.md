# estuda-enade-api

API do **Estuda ENADE**: atende o aluno (app iOS/Android e versão web para desktop) e o portal web do professor.
O frontend fica no repositório irmão [`estuda-enade-gui`](https://github.com/RegisMichael25/estuda-enade-gui). Documentos de concepção e protótipo ficam na pasta do projeto, em `../documentacao` e `../prototipos`.

Java 21 · Spring Boot 4 · PostgreSQL 16 · Flyway · JWT · OpenAPI (contrato primeiro)

## Rodando

Pré-requisitos: JDK 21 e Docker.

```bash
docker compose up -d database              # PostgreSQL em localhost:5435
./mvnw spring-boot:run
```

A API sobe em `http://localhost:8080`, cria o esquema e carrega dados de exemplo iguais aos do protótipo.
Não há perfis (dev, prod...): a configuração é uma só, em `application.properties`.

Segredos ficam fora do repositório, em um arquivo `.env` na raiz do projeto (lido pela API e pelo `docker-compose`):

```bash
echo "JWT_SECRET=$(openssl rand -base64 48)" > .env    # modelo em .env.example
```

Sem `JWT_SECRET` a API sobe do mesmo jeito, com um segredo aleatório que dura até o próximo reinício (as sessões abertas são renovadas pelo refresh token).

> Com Colima em vez do Docker Desktop, o comando é `docker-compose` (com hífen).

### Entrando

O login é sem senha: o usuário informa o e-mail, recebe um código e entra com ele.
Com `OTP_PROVIDER=log` (padrão) o código aparece no log da aplicação.
Os dados de exemplo incluem um professor e uma aluna já vinculados a uma turma; os dados de acesso dessas contas não ficam no repositório.

```bash
curl -X POST localhost:8080/api/v1/auth/codigo -H 'Content-Type: application/json' \
     -d '{"email":"<e-mail>"}'
curl -X POST localhost:8080/api/v1/auth/entrar -H 'Content-Type: application/json' \
     -d '{"email":"<e-mail>","codigo":"<código>"}'
# → { "accessToken": "...", "refreshToken": "...", "usuario": { ... } }
curl localhost:8080/api/v1/aluno/atividades -H 'Authorization: Bearer <accessToken>'
```

No primeiro acesso de um e-mail novo, `/auth/entrar` responde `422 CADASTRO_NECESSARIO`; reenvie com `nome` e `papel` (`ALUNO` ou `PROFESSOR`) usando o mesmo código.

### Cadastro de professor

O professor enxerga o gabarito de todo o banco, então o cadastro tem três camadas:

1. **Convite**: com `codigoConvite` válido no `/auth/entrar`, a conta já entra aprovada. O convite é de uso único, vale 7 dias e é criado por um professor aprovado (`POST /professor/convites`). Convite inválido responde `422 CONVITE_INVALIDO` e não cria a conta.
2. **Domínio institucional**: e-mail de um domínio listado em `CADASTRO_PROFESSOR_DOMINIOS` entra aprovado.
3. **Aprovação**: nos demais casos a conta é criada com `aprovado: false`. Ela não acessa nenhuma rota `/professor/...` (`403 CADASTRO_PENDENTE`) até um professor aprovado liberar em `POST /professor/professores/{id}/aprovar`. Depois de aprovado, o cliente chama `/auth/renovar` para receber um token com acesso.

As contas de exemplo já vêm aprovadas; é por elas (ou pelo domínio institucional) que entra o primeiro professor.

## Contrato OpenAPI e Swagger

A API é escrita **a partir do contrato**: o arquivo [`src/main/resources/static/openapi/estuda-enade-api.json`](src/main/resources/static/openapi/estuda-enade-api.json) descreve todas as rotas, requisições, respostas e erros.

- A cada build, o **OpenAPI Generator** (plugin do Maven) gera, em `target/generated-sources/openapi`:
  - as **interfaces das rotas** (`api.contrato.AuthApi`, `AlunoApi`, `ProfessorTurmaApi`...), que os controllers implementam;
  - as **classes de requisição** (`api.contrato.dto.EntrarRequest`, `QuestaoRequest`...), com as validações declaradas no contrato.
- As **respostas** continuam sendo os records de `api/dto`, ligados ao contrato no `pom.xml` (`schemaMappings`). O `ContratoOpenApiTest` falha se um campo divergir.
- O **Swagger UI** fica em `http://localhost:8080/swagger-ui.html` (público) e mostra esse mesmo arquivo. Use **Authorize** com o `accessToken` para testar as rotas protegidas.

Para criar ou mudar uma rota: altere o JSON do contrato, rode `./mvnw compile` e implemente o método novo no controller (o compilador aponta o que falta). Não edite o código gerado.

## Testes

```bash
./mvnw test
```

São testes de integração: sobem um PostgreSQL real com Testcontainers (Docker precisa estar rodando) e usam a API por HTTP.
Com Colima, exporte antes:

```bash
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## Estrutura

```
src/main/java/com/estudaenade/api
├── api/controller    implementam as interfaces geradas do contrato
├── api/dto           respostas (as requisições são geradas)
├── domain/model      entidades JPA
├── domain/repository acesso a dados
├── domain/service    regras de negócio
├── domain/enums
├── infra/security    JWT e filtro de autenticação
├── infra/codigo      envio do código de acesso (log ou SMTP)
├── config            segurança e CORS
└── exception         erros da API
src/main/resources
├── application.properties        configuração única
├── static/openapi                contrato OpenAPI (fonte das rotas e requisições)
└── db/migration                  Flyway (esquema e dados de exemplo)
```

## Endpoints

Prefixo `/api/v1`. Fora de `/auth`, todas as rotas exigem `Authorization: Bearer <accessToken>`.
Erros seguem `{ "codigo": "...", "mensagem": "..." }`.

### Acesso

| Método | Rota | O que faz |
|---|---|---|
| POST | `/auth/codigo` | Envia o código de acesso para o e-mail |
| POST | `/auth/entrar` | Entra com e-mail + código (cria a conta no primeiro acesso) |
| POST | `/auth/renovar` | Troca o refresh token por uma nova sessão |
| POST | `/auth/sair` | Revoga o refresh token |
| GET, PUT | `/eu` | Dados da conta · alterar nome |
| GET | `/cursos`, `/temas` | Catálogo usado nos filtros (`/temas?cursoId=` traz só os temas do curso) |

### Professor (`/professor/...`)

| Método | Rota | O que faz |
|---|---|---|
| POST | `/cursos`, `/temas` | Cria curso ou tema (o tema pertence a um curso: `{ nome, cursoId }`) |
| GET | `/professores/pendentes` | Professores aguardando aprovação |
| POST | `/professores/{id}/aprovar` | Aprova o cadastro de um professor pendente |
| GET, POST | `/convites` | Meus convites de professor · gera um convite (o código só aparece na criação) |
| GET | `/painel` | Painel inicial: números, turmas ativas e atividades recentes |
| GET, POST | `/turmas` | Lista (só ativas; `?incluirArquivadas=true` traz todas) · cria turma (gera o código de convite) |
| GET, PUT | `/turmas/{id}` | Detalhe com alunos · edita |
| POST | `/turmas/{id}/convite` | Gera novo código; o antigo deixa de valer |
| POST | `/turmas/{id}/arquivar`, `/reativar` | Arquiva a turma · volta a turma para a lista |
| DELETE | `/turmas/{id}/alunos/{alunoId}` | Remove o aluno da turma |
| GET | `/turmas/{id}/resumo` | Painel: alunos, banco, atividades abertas, média |
| GET | `/turmas/{id}/alunos/{alunoId}/desempenho` | Desempenho do aluno nas atividades da turma |
| GET, POST | `/turmas/{id}/atividades` | Lista · cria atividade (rascunho) |
| GET | `/questoes/anos` | Anos das questões visíveis no banco (`cursoId` opcional), para o filtro |
| GET, POST | `/questoes` | Banco (`minhas`, `cursoId`, `temaId`, `ano`, `busca`, `status`, `sinalizadas`, `pagina`, `tamanho`) · cria rascunho |
| GET, PUT | `/questoes/{id}` | Detalhe · edita (só o autor) |
| POST | `/questoes/{id}/publicar`, `/ocultar` | Publica no banco compartilhado · oculta |
| GET, POST | `/questoes/{id}/sinalizacoes` | Lista · sinaliza possível erro |
| POST | `/questoes/{id}/sinalizacoes/resolver` | Autor marca as sinalizações como resolvidas |
| GET, PUT, DELETE | `/atividades/{id}` | Detalhe com questões · edita ou exclui rascunho |
| POST | `/atividades/{id}/publicar`, `/encerrar` | Publica para a turma · fecha para novas tentativas |
| GET | `/atividades/{id}/resultados` | Resultado por aluno, por tema e média da turma |
| GET, POST | `/atividades/{id}/comentarios` | Mural da atividade · comenta |
| PUT, DELETE | `/comentarios/{id}` | Edita o próprio comentário · apaga (o próprio ou qualquer um da turma) |

### Aluno (`/aluno/...`)

| Método | Rota | O que faz |
|---|---|---|
| POST | `/turmas/entrar` | Entra na turma com o código do convite |
| GET | `/turmas` | Minhas turmas |
| DELETE | `/turmas/{id}` | Sai da turma (tentativas e resultados ficam guardados) |
| GET | `/atividades`, `/atividades/{id}` | Atividades publicadas e minhas tentativas |
| POST | `/atividades/{id}/tentativas` | Inicia a tentativa (ou retoma a que está em andamento) |
| GET | `/tentativas` | Histórico de tentativas |
| GET | `/tentativas/{id}` | Questões, respostas marcadas e tempo restante |
| PUT | `/tentativas/{id}/respostas/{questaoId}` | Marca a alternativa (`null` deixa em branco) |
| POST | `/tentativas/{id}/enviar` | Envia e devolve o resultado |
| GET | `/tentativas/{id}/resultado` | Nota, gabarito, explicações e acertos por tema |
| GET, POST | `/atividades/{id}/comentarios` | Mural da atividade (depois de enviar a primeira tentativa) · comenta |
| PUT, DELETE | `/comentarios/{id}` | Edita · apaga o próprio comentário |
| GET | `/pratica/anos` | Anos com questões publicadas (`cursoId` opcional) |
| GET | `/pratica/questoes` | Questões para prática livre (`cursoId`, `temaId`, `ano`, `busca`) |
| POST | `/pratica/questoes/{id}/responder` | Responde e recebe gabarito e explicação |
| POST | `/questoes/{id}/sinalizacoes` | Sinaliza possível erro |
| GET | `/desempenho` | Acertos gerais e por tema |

## Regras adotadas

O documento de visão deixou alguns pontos em aberto. Esta versão assume:

- **Cronômetro no servidor.** O fim do tempo é fixado ao iniciar a tentativa e não pausa. Cada resposta é gravada ao ser marcada, então fechar o app ou o navegador, perder a conexão ou trocar de aparelho não perde nada: o aluno retoma a mesma tentativa. Ao fim do tempo, o que estiver marcado é enviado automaticamente (na próxima chamada do aluno ou por uma rotina a cada 30 s).
- **Questão em branco conta como erro.**
- **Gabarito só depois.** Na prática livre, depois de responder a questão; na atividade, depois de concluir a tentativa. Antes disso a API não envia gabarito nem explicação. Por isso, as questões de uma atividade aberta ficam fora da prática livre do aluno da turma até ele concluir uma tentativa dela (`QUESTAO_EM_ATIVIDADE`).
- **Novas tentativas:** `maxTentativas` da atividade depende do tipo. **Lista** (treino): de 1 a 5, ou `null` para ilimitadas. **Simulado**: 1 ou 2, sem ilimitadas. Fora disso, `422 TENTATIVAS_INVALIDAS` ao criar, editar ou publicar (a publicação também barra rascunhos antigos fora da regra). Atividades já publicadas não mudam. Nos resultados do professor vale a **melhor** tentativa de cada aluno.
- **Atividade publicada não muda.** Só rascunhos podem ser editados ou excluídos.
- **Questão:** só o autor edita, publica e oculta. Sinalização marca para revisão e não tira a questão do banco; a lista de sinalizações mostra o motivo e a data, sem identificar quem sinalizou. Questão oculta sai do banco e da prática livre, mas continua nas atividades que já a usam.
- **Turma arquivada:** some da lista padrão do professor e da lista do aluno, não aceita novos alunos, novo convite nem novas atividades (`422 TURMA_ARQUIVADA`). As atividades publicadas passam a encerradas (tentativas em andamento seguem até o envio ou o fim do tempo). Alunos, atividades e resultados continuam guardados. Reativar devolve a turma com o mesmo código; as atividades encerradas continuam encerradas.
- **Mural de comentários:** cada atividade (lista ou simulado) tem um mural aberto à turma, em ordem de envio. O aluno só vê e comenta depois de enviar a primeira tentativa (antes disso, 404); o professor da turma vê sempre. O autor edita (fica marcado como editado) e apaga o que escreveu; o professor da turma apaga qualquer comentário. Turma arquivada deixa o mural só para leitura (`422 TURMA_ARQUIVADA` ao comentar ou editar; apagar continua permitido). Rascunho não recebe comentário (`422 ATIVIDADE_NAO_PUBLICADA`). Aluno que sai da turma perde o acesso, e os comentários dele ficam.
- **Professor:** só atua depois de aprovado: por convite de outro professor, por e-mail de domínio institucional ou por aprovação de um professor já aprovado (veja "Cadastro de professor"). Qualquer professor aprovado pode aprovar e convidar; não há administrador nesta versão.

## Configuração

Tudo tem valor padrão em `application.properties`; defina a variável de ambiente só para mudar.

| Variável | Para quê | Padrão |
|---|---|---|
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | Banco PostgreSQL | banco do `docker-compose.yml` (`localhost:5435`) |
| `JWT_SECRET` | Segredo do token (mínimo 32 caracteres). Defina no `.env` | sem valor: segredo aleatório a cada subida |
| `CORS_ALLOWED_ORIGINS` | Origens da versão web do aluno e do portal do professor, separadas por vírgula | localhost e rede local |
| `OTP_PROVIDER` | `log` ou `smtp` | `log` |
| `OTP_CODIGO_FIXO` | Código de acesso fixo; vazio gera um código aleatório a cada pedido | valor em `application.properties` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `OTP_REMETENTE` | Envio do código por e-mail quando `OTP_PROVIDER=smtp` | sem envio |
| `CADASTRO_PROFESSOR_DOMINIOS` | Domínios de e-mail da instituição: professor desses domínios entra aprovado | nenhum |
| `CONVITE_PROFESSOR_VALIDADE_DIAS` | Validade do convite de professor | `7` |
