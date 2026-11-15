# Cadastro de pessoa assistida

Protótipo de cadastro de pessoa assistida por conversa guiada, desenvolvido para a **Defensoria Pública do Estado de Goiás**. A pessoa responde a uma pergunta por vez, confere um resumo editável e confirma o envio. O backend em Java controla o fluxo, valida os dados e grava o cadastro. Texto livre pode ser interpretado por um modelo de IA, sempre com revisão e sem que a IA decida nada.

É uma **demonstração técnica com dados fictícios**. O cadastro e o comprovante não têm validade oficial, não criam atendimento nem processo e não se integram a sistemas da Defensoria. O frontend segue a identidade visual do portal institucional (https://www2.defensoria.go.def.br/): marca oficial, verdes `#007B2F` e `#95AE23`, cinza `#565656` e fonte Roboto.

## Sumário

- [Como funciona](#como-funciona)
- [Arquitetura](#arquitetura)
- [Como rodar](#como-rodar)
- [Variáveis de ambiente](#variáveis-de-ambiente)
- [Assistência por IA](#assistência-por-ia)
- [API](#api)
- [Modelo de dados](#modelo-de-dados)
- [Testes](#testes)
- [Decisões](#decisões)
- [Limitações](#limitações)
- [O que fica para evolução](#o-que-fica-para-evolução)

## Como funciona

1. A pessoa abre a página, lê a apresentação (com o aviso de protótipo) e escolhe **Começar**. Isso cria uma sessão privada.
2. O servidor decide a próxima pergunta. Há entradas próprias para escolha, texto, data, CPF, telefone, e-mail, CEP, valor em reais e números.
3. Para CPF, nascimento, endereço e contato existem respostas de ausência (não possuo, não sei informar, não estou com o documento, sem número). Nada é inventado para preencher campo: ausência vira valor nulo com o motivo registrado.
4. Ao responder tudo, abre o **resumo por seções**, com ação **Corrigir** em cada item. Toda alteração gera uma nova versão e exige nova revisão.
5. **Confirmar cadastro** é a única ação que grava de forma definitiva. Digitar "sim" em qualquer etapa não salva nada.
6. O comprovante mostra um identificador da operação e a data. Ele não é protocolo oficial nem permite consultar o cadastro.

Cadastro para outra pessoa é suportado de forma simplificada: o nome de quem preenche e o vínculo declarado ficam separados dos dados da titular, e o vínculo não é conferido.

### Estados e modos

| Estado | Significado |
|---|---|
| `EM_PREENCHIMENTO` | Perguntas em andamento, dados em rascunho |
| `AGUARDANDO_ESCLARECIMENTO` | Uma resposta precisa de escolha (confirmar interpretação da IA ou apagar dados dependentes) |
| `EM_REVISAO` | Resumo disponível. Qualquer alteração gera nova versão |
| `CONFIRMADO` | Cadastro gravado. Nova confirmação devolve o mesmo comprovante |
| `CANCELADO` / `EXPIRADO` | Rascunho removido |

`ESTRUTURADO` e `ASSISTIDO_POR_IA` são **modos de coleta**, e não estados. Uma falha da IA troca o modo e preserva o rascunho.

## Arquitetura

```
Navegador ── React + TypeScript + Vite (nginx no Compose)
    │  /api, cookie HttpOnly + token CSRF
    ▼
API Spring Boot 3 (Java 21), monólito modular
  ├─ cadastro   sessão, rascunho versionado, entidades, confirmação gravada
  ├─ conversa   roteiro de perguntas, validações, API REST, limites, confirmação
  └─ integracao interpretador de IA (Chat Completions), eventos técnicos
    │                         │
    ▼                         ▼
PostgreSQL 16 (Flyway)    Provedor de IA externo (só o backend fala com ele)
```

- **Frontend:** componentes `CabecalhoInstitucional`, `ChatCadastro`, `EntradaConversa`, `ResumoCadastro`, `ComprovanteCadastro` e `RodapeInstitucional`, com tema centralizado em tokens (`frontend/src/tema/tokens.css`). Sem biblioteca de UI. A conversa fica só em memória no navegador, sem `localStorage`.
- **Backend:** o roteiro de perguntas é uma lista declarativa (`RoteiroCadastro`). A etapa atual não é guardada: é sempre a primeira pergunta aplicável ainda sem resposta, o que mantém o fluxo coerente após correções.
- **Sessão:** segredo aleatório de 256 bits em cookie `HttpOnly` e `SameSite=Strict`. O banco guarda apenas o hash. Expira por inatividade (30 minutos, configurável) e uma rotina apaga rascunhos expirados.
- **Concorrência:** a versão do rascunho é controlada pelo banco, `mensagemId` garante idempotência por turno, uma sessão só executa uma operação de resposta por vez e a confirmação trava a linha da sessão. A chamada ao provedor de IA acontece fora de qualquer transação.

## Como rodar

### Com Docker (recomendado)

Requer Docker com Compose.

```bash
cp .env.example .env
docker compose up --build
```

- Interface: http://localhost:8080
- API: http://localhost:8081
- As portas são publicadas apenas em `127.0.0.1` e mudam no `.env` (`WEB_PORT`, `API_PORT`, `DB_PORT`).
- `DB_PASSWORD` vazio usa uma senha padrão adequada só ao banco local de demonstração.
- Sem `LLM_API_KEY`, o cadastro funciona por perguntas diretas e a tela informa que a IA não está configurada.

Para apagar os dados de demonstração deste projeto:

```bash
docker compose down -v
```

### Execução local

Requer JDK 21, Node 22 ou superior e Docker (para o PostgreSQL).

```bash
docker compose up -d db

cd backend
./mvnw spring-boot:run

cd frontend
npm install
npm run dev
```

O Vite serve a interface em http://localhost:5173 e encaminha `/api` para `localhost:8081`. O proxy preserva o cabeçalho `Host` de propósito: com ele alterado, o CORS da API trataria a chamada como de outra origem. Em desenvolvimento a API aceita cookie sem `Secure`. Em HTTPS use `SESSAO_COOKIE_SEGURO=true`.

No PowerShell, para o Maven use `.\mvnw.cmd`.

## Variáveis de ambiente

Copie `.env.example` para `.env`. O `.env` está no `.gitignore` e **nunca** deve ser versionado.

| Variável | Padrão | Função |
|---|---|---|
| `WEB_PORT`, `API_PORT`, `DB_PORT` | 8080, 8081, 5433 | Portas publicadas em `127.0.0.1` |
| `DB_NAME`, `DB_USER`, `DB_PASSWORD` | cadastro, cadastro, (vazio) | Banco de dados |
| `SESSAO_EXPIRACAO_MINUTOS` | 30 | Inatividade até expirar a sessão |
| `SESSAO_COOKIE_SEGURO` | false | `true` quando atrás de HTTPS |
| `CORS_ORIGENS` | (vazio) | Origens extras, separadas por vírgula. Vazio aceita só a mesma origem |
| `LIMITE_RESPOSTAS_POR_SESSAO` | 60 | Respostas por sessão |
| `LIMITE_RESPOSTAS_POR_MINUTO` | 10 | Respostas por minuto por sessão |
| `LIMITE_REQUISICOES_POR_ORIGEM_POR_MINUTO` | 120 | Teto por origem |
| `LLM_API_KEY` | (vazio) | Chave do provedor. **Só no backend** |
| `LLM_BASE_URL` | `https://api.mistral.ai/v1` | Endpoint compatível com Chat Completions |
| `LLM_MODEL` | `mistral-small-latest` | Modelo |
| `LLM_TIMEOUT_SECONDS` | 15 | Limite por chamada |
| `LLM_MAX_OUTPUT_TOKENS` | 350 | Limite de saída |

## Assistência por IA

A IA é um **interpretador de respostas**, não o controlador da aplicação. Ela só propõe valores. Quem valida, decide a próxima pergunta, exige a revisão e grava é o Java.

- **Provedor:** interface `InterpretadorRespostas` e uma implementação para APIs compatíveis com Chat Completions (`InterpretadorChatCompletions`). Trocar de provedor exige apenas mudar `LLM_BASE_URL`, `LLM_MODEL` e `LLM_API_KEY`, desde que o endpoint seja compatível (autenticação Bearer e `response_format` JSON).
- **O que vai ao modelo:** apenas o texto livre de **município** e **ocupação**, a pergunta atual e a lista de campos permitidos no turno. Nome, CPF, nascimento, telefone, e-mail, endereço, histórico e dados de sessão nunca são enviados. Texto com CPF, telefone ou e-mail escrito fora do campo próprio é recusado antes da chamada. Esse filtro reduz a exposição, mas não garante anonimização.
- **Aviso:** antes do primeiro uso o chat explica o que será enviado a um serviço externo e oferece **Continuar com perguntas estruturadas**.
- **Contrato da saída:** `operacao` (`PROPOR_VALORES`, `SOLICITAR_ESCLARECIMENTO` ou `FORA_DE_ESCOPO`), `candidatos[campo, valor, trechoOrigem]` e `precisaEsclarecimento`. Propriedades extras, tipos incompatíveis, campos fora da lista do turno, origem que não está no texto da pessoa e operações como confirmar são rejeitados e a resposta inteira é descartada.
- **Regras do domínio:** cada candidato passa de novo pelas regras do campo, não sobrescreve resposta existente e, havendo mais de um dado ou dúvida, volta para a pessoa confirmar.
- **Falhas:** sem chave, 401/403, créditos, 429, timeout, 5xx, JSON inválido ou truncado: nenhuma nova tentativa automática, o modo passa a `ESTRUTURADO`, o rascunho é preservado e a tela avisa. Nada é simulado como resposta do modelo.
- **Segredo:** a chave existe só no processo da API. Não há variável `VITE_` com segredo, ela não aparece no pacote do frontend, nas respostas da API, nos logs nem na representação textual da configuração (há testes para cada ponto).

### Teste de chamada real

`InterpretadorRealTest` chama o provedor configurado com texto fictício e só roda quando `LLM_API_KEY` está no ambiente. Para executá-lo carregue o `.env` no shell **apenas para esse teste**, porque os demais testes esperam o ambiente sem chave:

```bash
set -a; . ./.env; set +a
cd backend && ./mvnw test -Dtest=InterpretadorRealTest
```

Provedores locais ou lentos podem estourar o limite de 15 segundos na primeira chamada, enquanto o modelo é carregado. Nesse caso o cadastro simplesmente segue por perguntas diretas. Aumente `LLM_TIMEOUT_SECONDS` ou use um provedor hospedado.

## API

Todas as rotas ficam sob `/api/v1/cadastro` e dependem do cookie de sessão. Não existe listagem, busca por CPF nem rota que receba um identificador de cadastro. Respostas usam `Cache-Control: no-store`. Mutações exigem o cabeçalho `X-XSRF-TOKEN` (valor do cookie `XSRF-TOKEN`).

| Método e rota | Função |
|---|---|
| `POST /sessao` | Inicia a sessão, define o cookie e devolve a abertura e a primeira pergunta |
| `GET /atual` | Estado, rascunho e próxima pergunta da sessão atual |
| `POST /respostas` | `mensagemId`, `versao` e a resposta (`valor` ou `opcao`) |
| `PATCH /campos/{campo}` | Corrige um campo já respondido (`versao` e a nova resposta) |
| `GET /revisao` | Resumo por seções, pendências e a versão a revisar |
| `POST /confirmacao` | `versaoRevisada` e `chaveIdempotencia`. Grava e devolve o comprovante |
| `DELETE /sessao` | Encerra a sessão e remove o rascunho. Não apaga cadastro confirmado |

Códigos de erro, todos em JSON `{codigo, mensagem, campo?}` e sem detalhes internos: `400` entrada ou resposta inválida, `401` sem sessão válida, `403` token CSRF ausente, `409` versão desatualizada, estado inválido ou conflito cadastral (sem revelar terceiros), `410` sessão expirada, `429` limite de uso (com `Retry-After` quando há espera), `500` falha interna. `GET /actuator/health` é o único endpoint de diagnóstico exposto.

## Modelo de dados

Migrações em `backend/src/main/resources/db/migration` (Flyway).

| Tabela | Conteúdo |
|---|---|
| `sessao_cadastro` | Hash do segredo, estado, modo, versão e prazos |
| `rascunho_cadastro` | Dados e ausências em JSONB, versão e esclarecimento pendente |
| `mensagem_processada` | `mensagemId` por sessão, para idempotência (sem o texto) |
| `pessoa_cadastro` | Identificação, contato e localização. CPF `NULL` quando ausente, com índice único parcial |
| `declaracao_socioeconomica` | Retrato datado: competência, ocupação, renda `NUMERIC(15,2)`, núcleo, dependentes e moradia |
| `representacao_declarada` | Quem preencheu e o vínculo declarado, separado da titular |
| `confirmacao_cadastro` | Uma por sessão, com versão revisada e chave de idempotência |
| `evento_tecnico` | Tipo, status, modo, modelo e duração, sem dado pessoal, chave ou conversa |

Zero é um valor econômico, e não ausência: `Não tenho renda` grava `0.00`, e `Não sei informar` grava `NULL` com o motivo.

## Testes

```bash
cd backend && ./mvnw verify          # unitários e integração (requer Docker para o PostgreSQL)
cd frontend && npm run lint && npm test && npm run build
```

O backend usa JUnit 5, Testcontainers (PostgreSQL 16, o mesmo do Compose) e um servidor HTTP simulado para o provedor de IA. O frontend usa Vitest, Testing Library e axe-core.

Cenários cobertos: fluxo comum e com ausências, texto com dois dados, CPF inválido (recusado no backend), CPF ausente e duplicado, correção que preserva os demais campos, duplo clique e confirmações simultâneas, sessão A contra sessão B, sessão expirada, JSON inesperado, injeção de instruções, 401/429/timeout do provedor sem nova tentativa, segredo fora do pacote, das respostas e dos logs, falha na transação sem cadastro parcial, limites de uso, acessibilidade (axe e jornada só com teclado) e conformidade do tema com as cores do portal.

O teste de ponta a ponta do fluxo, da API até o banco, está em `ConfirmacaoTest`. O contraste das cores do tema foi calculado: todo texto passa de 4,5:1.

## Decisões

- **Mistral no lugar de Qwen/Hugging Face.** A ata de requisitos prevê Qwen via Hugging Face Inference Providers. Optou-se por usar a **Mistral por um adaptador compatível com Chat Completions**, mantendo o restante da ata (contrato de saída, validações, fallback, privacidade). Como o adaptador só depende de URL, modelo e chave, o Hugging Face ou qualquer outro endpoint compatível pode ser usado sem mudança de código.
- **Sem biblioteca de UI.** O tema da Defensoria é aplicado diretamente em CSS com tokens, evitando sobrescrever o tema padrão de uma biblioteca.
- **Sessão própria em vez de `HttpSession`.** O segredo vai só no cookie e o banco guarda o hash, o que permite expirar e limpar sessões por consulta.
- **Retomada.** Ao recarregar a página, a conversa em memória se perde e **Começar** abre uma nova sessão. É consequência de não persistir a transcrição.

## Limitações

- **Demonstração, não produção.** Não há verificação de identidade. CPF válido não comprova titularidade, e não há consulta à Receita. Não é possível recuperar ou alterar um cadastro já gravado.
- **Sem painel interno.** Não há listagem, consulta nem administração de cadastros.
- **Representação:** o vínculo é apenas declarado, sem validação de poderes ou documentos.
- **Limites em memória.** O limite por sessão, por minuto e por origem vale para uma única instância da aplicação. Com várias instâncias seria preciso estado compartilhado. Os números são hipóteses iniciais, não capacidade medida.
- **Origem do acesso.** O limite por origem só usa `X-Forwarded-For` quando `LIMITES_CONFIAR_EM_PROXY=true`, o que o Compose ativa por estar atrás do nginx. Exponha a API diretamente apenas com essa opção desligada.
- **Provedor de IA.** Antes de qualquer uso real é preciso avaliar finalidade, contrato, retenção e localização do processamento. Planos gratuitos podem usar as entradas para treinamento e endpoints `http` trafegam a chave e o texto sem criptografia: use apenas dados fictícios. A integração real foi validada por testes com servidor simulado. A chamada ao provedor depende de chave e rede do ambiente de quem executa, e o teste correspondente fica pulado quando não há chave.
- **Anexos e documentos:** sem upload nem conferência documental. O checklist informativo de documentos, previsto como item opcional da ata, não foi implementado.
- **Dados fictícios.** Não use dados de pessoas reais. O README e a tela deixam claro que o comprovante do protótipo não tem validade oficial.

## O que fica para evolução

Representação completa, upload e conferência de documentos, autenticação de cidadão, recuperação e retificação de cadastro, assinatura e aceite com validade, painel interno e triagem, avaliação econômica com regras e vigência, agenda, comunicação por canais autorizados, integração com sistemas da Defensoria, estado compartilhado para limites em vários servidores, OpenAPI e consulta de CEP.
