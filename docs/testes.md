# Testes

## Como rodar

```bash
cd backend && ./mvnw verify          # requer Docker (PostgreSQL via Testcontainers)
cd frontend && npm run lint && npm test && npm run build
```

No PowerShell, use `.\mvnw.cmd` no lugar de `./mvnw`.

Resultado da última execução completa: **backend com 413 testes, 4 ignorados** (os que dependem de chave real), **frontend com 103 testes** e **5 testes de navegador**, todos passando.

## Estratégia

| Camada | Como é testada |
|---|---|
| Regras puras (validadores, CPF, filtros, catálogo) | Testes unitários |
| API e fluxo completo | Spring Boot Test com MockMvc e PostgreSQL 16 real em contêiner (Testcontainers), o mesmo do Compose |
| IA | `InterpretadorFalso` programável nos testes de fluxo. `MockWebServer` nos testes do cliente HTTP |
| CEP | `ConsultaCepFalsa` nos testes de fluxo. `MockWebServer` (dois servidores) nos testes dos provedores |
| Relógio | `RelogioDeTeste` controlável, para expiração e competência |
| Frontend | Vitest com Testing Library, `fetch` simulado e axe-core |
| Navegador | Playwright contra o Docker Compose no ar, pela interface e pelo nginx, como o usuário e como um cliente hostil |

Os dados dos testes são inventados.

## Backend

| Classe | Testes | O que cobre |
|---|---:|---|
| `ValidadoresTest` | 89 | Nome, data, CPF, telefone (todos os DDDs, celular, fixo, repetidos, internacional), e-mail, CEP, renda e núcleo |
| `InterpretadorChatCompletionsTest` | 50 | Requisição, Bearer, contrato estrito, cada regra do `ValidadorSaida`, 401/402/429/5xx, timeout, JSON truncado e segredo fora da mensagem de erro |
| `ServicoCepTest` | 27 | ViaCEP, fallback para BrasilAPI, timeout, resposta inválida, não encontrado, indisponível, cache (acerto, validade, tamanho) e evento sem CEP |
| `AssistenciaIaTest` | 30 | Município e ocupação por IA, confirmação, UF, ambiguidade, falhas com fallback, ajuda por catálogo |
| `AssistenciaIaRelatoTest` | 24 | Relato com vários campos, confirmação, recusa, termos sensíveis, texto não guardado |
| `RoteiroCadastroTest` | 20 | Próxima pergunta, aplicabilidade e ordem |
| `ConfirmacaoTest` | 19 | Fluxo completo até o banco, comprovante, idempotência, confirmações simultâneas, duplicidade de CPF, renda zero, outra pessoa |
| `CadastroApiTest` | 17 | Cabeçalhos, CSRF, versão, reenvio e correção |
| `CepApiTest` | 14 | Endereço achado e confirmado, recusado, não encontrado, fora do ar, divergência de município e UF, reenvio sem nova consulta |
| `CpfTest` | 13 | Dígitos verificadores e sequências repetidas |
| `ConteudoSensivelTest`, `ContemDadosPessoaisTest` | 12, 10 | Filtros de texto livre |
| `SessaoPrivadaTest` | 10 | Cookie, hash, expiração e `no-store` |
| `PersistenciaCadastroTest` | 9 | Restrições do banco |
| `ServicoConversaTest` | 9 | Resumo e fluxo de serviço |
| `LimitesRespostasTest` | 7 | Limites por sessão e por minuto |
| `EstadoSessaoTest` | 6 | Transições de estado |
| `IsolamentoEntreSessoesTest` | 4 | Sessão A não acessa sessão B |
| `ContatoApiTest` | 3 | Telefone inválido, CPF recusado no servidor, "Não tenho telefone" |
| Outras | 40 | Vetores compartilhados de CPF e telefone, chave de origem (inclusive cadeia forjada), tamanho do mapa de origens, verificação do perfil de produção, limite por origem, catálogo de ajuda, operação única por sessão, falha na transação, logs sem dados pessoais, segredo fora do navegador, propriedades da IA, inicialização do aplicativo e `InterpretadorRealTest` (4, ignorados sem chave) |

### Cenários que merecem destaque

- **Falha na transação:** se a gravação da confirmação falha no meio, nada fica gravado e nenhum comprovante é emitido (`FalhaNaTransacaoTest`).
- **Corrida:** duas confirmações simultâneas gravam um único cadastro. Duas respostas simultâneas na mesma sessão recebem `429` na segunda (`OperacaoUnicaPorSessaoTest`).
- **Injeção de instruções:** respostas do modelo com campos extras, valores fora das opções ou trechos que não estão no texto são descartadas por inteiro.
- **Segredo:** a chave não aparece em respostas, logs, `toString` nem no pacote do frontend.

## Frontend

| Arquivo | O que cobre |
|---|---|
| `validacao.test.ts` | Máscara e validação de CPF e telefone, e a data de hoje no fuso de Brasília |
| `vetores.test.ts` | Os casos de `testes-compartilhados/validacao.json`, os mesmos lidos pelo backend (`VetoresCompartilhadosTest`): interface e servidor aceitam e recusam o mesmo texto e a mensagem contém o mesmo trecho |
| `EntradaConversa.test.tsx` | Máscara ao digitar, apagar atravessando símbolos, erro acessível e que some ao corrigir, ausência sem validar e limite da data de nascimento que acompanha a virada do dia |
| `ChatCadastro.test.tsx` | Envio único, duplo clique, texto exibido como texto, sessão expirada, modo de IA |
| `RelatoEConfirmacao.test.tsx` | Relato, lista "Entendi assim" e ajuda |
| `PainelRespostas.test.tsx` | Painel durante o preenchimento, edição, bloqueio com escolha pendente |
| `TelaConferencia.test.tsx` | Conferência em tela cheia, selo de ausência, cancelamento, acessibilidade |
| `RevisaoCadastro.test.tsx` | Resumo, correção, recusa do servidor, confirmação, comprovante, falha de rede com a mesma chave, conflito e encerramento |
| `PaginaInicial.test.tsx` | Apresentação e aviso de protótipo |
| `Acessibilidade.test.tsx` | axe nas telas e jornada completa só com teclado |
| `Tema.test.ts` | Marca e cores do portal, sem cor solta nos estilos |
| `Nginx.test.ts` | Cabeçalhos de segurança, CSP sem script inline nem `eval`, e `X-Forwarded-For` definido pela conexão |
| `App.test.tsx` | Montagem do aplicativo |

O axe roda sem a regra de contraste, porque o jsdom não calcula estilos. O contraste das cores do tema foi calculado e todo texto passa de 4,5:1.

## Testes de navegador (`e2e/`)

Rodam contra o stack completo (banco, API e nginx), sem chave de IA e sem mocks.

| Arquivo | O que cobre |
|---|---|
| `jornada.spec.ts` | Da página inicial ao comprovante: perguntas, ausência de nascimento, conferência em tela cheia sem conversa nem painel, selo "Ausência declarada", correção de telefone barrada no cliente (DDD inexistente) e depois aceita, confirmação, foco no título, comprovante e encerramento. Falha se a política de conteúdo do nginx bloquear algo da aplicação. Usa um CPF válido novo a cada execução, porque o Compose guarda os dados |
| `seguranca.spec.ts` | Cabeçalhos de segurança e versão do nginx oculta, `401` sem sessão com `no-store` e sem cabeçalho duplicado pelo proxy, `403` sem token CSRF e a regressão do `X-Forwarded-For`: com o cabeçalho forjado a cada requisição, o teto por origem tem de valer |

O endereço pelo CEP fica de fora, de propósito: dependeria de serviços públicos na internet. Esse fluxo é coberto no backend com servidores simulados.

A regressão do `X-Forwarded-For` foi verificada de duas formas: passa com a configuração atual e **falha** (0 requisições limitadas) com o nginx e o filtro anteriores à correção.

Como rodar localmente, com o Compose no ar e o Google Chrome instalado. A jornada faz mais respostas por minuto do que o teto padrão de 10, por isso os tetos sobem só para o teste:

```bash
LIMITE_RESPOSTAS_POR_MINUTO=100 LIMITE_REQUISICOES_POR_ORIGEM_POR_MINUTO=100 docker compose up --build -d --wait
cd e2e && npm ci && E2E_LIMITE_ORIGEM=100 npx playwright test
```

`E2E_BASE_URL` muda o endereço testado (padrão `http://localhost:8080`) e `E2E_LIMITE_ORIGEM` informa o teto por origem do Compose (padrão 120). Com `CI=1` o Playwright usa o Chromium dele (`npx playwright install chromium`) em vez do Chrome instalado.

## Integração contínua

`.github/workflows/ci.yml` roda a cada push em `main` e em todo pull request, com três jobs independentes:

| Job | O que roda |
|---|---|
| Backend | `./mvnw -B -ntp verify` (Java 21). Os testes de integração sobem o PostgreSQL pelo Testcontainers, no Docker do runner |
| Frontend | `npm ci`, `npm run lint`, `npm test` e `npm run build` (Node 22) |
| Navegador | `cp .env.example .env`, `docker compose up --build -d --wait`, e os testes de `e2e/` com o Chromium do Playwright. Se falhar, publica os logs do Compose e o relatório do Playwright |

A CI só tem permissão de leitura do repositório, não usa segredos (o cadastro roda sem chave de IA) e cancela a execução anterior da mesma branch quando chega uma nova.

## Teste de chamada real à IA

`InterpretadorRealTest` chama o provedor configurado com texto fictício e só roda se `LLM_API_KEY` estiver no ambiente. Os demais testes esperam o ambiente **sem** chave, então carregue o `.env` apenas para esse teste:

```bash
set -a; . ./.env; set +a
cd backend && ./mvnw test -Dtest=InterpretadorRealTest
```

## O que não é coberto

- O fluxo de CEP real não entra nos testes de navegador, porque dependeria de serviços externos. Ele foi conferido à mão e é coberto no backend com servidores simulados.
- Os testes de navegador usam um só navegador (Chromium) e um só tamanho de tela. Responsividade e contraste de cores foram conferidos à mão.
- Não há teste de carga nem medição de capacidade. Os limites de uso são hipóteses.
- Os testes de CEP usam servidores simulados. O formato real dos dois provedores foi conferido manualmente com um CEP público, mas sem teste automatizado contra a internet.
