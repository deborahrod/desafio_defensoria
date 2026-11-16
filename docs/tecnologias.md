# Tecnologias

Versões conforme `backend/pom.xml`, `frontend/package.json` e `docker-compose.yml`.

## Backend

| Item | Uso | Por quê |
|---|---|---|
| Java 21 | Linguagem e plataforma | Versão com suporte de longo prazo, `record`, `sealed` e `switch` com padrões, usados no código |
| Spring Boot 3.5.16 | Web, JPA, Security, Validation, Actuator | Convenções conhecidas, testes de integração prontos e segurança configurável |
| Spring Security | CSRF por cookie, CORS, cabeçalhos | Sem login: a autorização vem do cookie de sessão próprio. O Security cuida de CSRF e cabeçalhos padrão |
| Spring Data JPA / Hibernate | Persistência | Rascunho com colunas `JSONB` mapeadas |
| PostgreSQL 16 | Banco | Restrições, índice único parcial (CPF) e `JSONB` |
| Flyway | Migrações `V1` a `V4` | Esquema versionado e validado na subida (`ddl-auto: validate`) |
| Maven Wrapper | Build | Não exige Maven instalado |

Não há outras bibliotecas de domínio: validações de CPF, telefone e CEP, o roteiro de perguntas e o contrato da IA são código próprio.

## Frontend

| Item | Uso | Por quê |
|---|---|---|
| React 19 | Interface | Componentes pequenos e testáveis |
| TypeScript | Tipagem | Os tipos de `api/tipos.ts` espelham o contrato da API |
| Vite 8 | Build e servidor de desenvolvimento | O proxy de `/api` preserva o cabeçalho `Host` para o CORS da API |
| Material UI 9 (`@mui/material`) e Emotion | Somente o `Stepper` da barra de etapas | Componente pronto e acessível, com tema próprio derivado dos tokens. O resto é CSS Modules |
| CSS Modules e tokens (`tema/tokens.css`) | Identidade visual | Cores e fonte do portal da Defensoria em um só lugar. Um teste impede cor solta nos estilos |
| Vitest 5, Testing Library, jsdom | Testes de componentes | |
| axe-core | Acessibilidade nos testes | |
| oxlint | Lint | |

A conversa existe só em memória no navegador. Não há `localStorage` nem `sessionStorage` no código de produção.

## Infraestrutura

`docker-compose.yml` define três serviços no projeto `desafio-defensoria`:

| Serviço | Imagem ou build | Porta padrão (somente `127.0.0.1`) |
|---|---|---|
| `db` | `postgres:16-alpine`, limite de 512 MB | 5433 |
| `api` | `backend/Dockerfile` | 8081 |
| `web` | `frontend/Dockerfile`, nginx servindo o build e encaminhando `/api/` para a API | 8080 |

O nginx repassa `Host`, `X-Forwarded-For` e `X-Forwarded-Proto`. O Compose liga `LIMITES_CONFIAR_EM_PROXY` porque a API fica atrás dele.

## Serviços externos (opcionais)

| Serviço | Para quê | Quem chama |
|---|---|---|
| API compatível com Chat Completions (padrão Mistral, validado também com Gemini) | Interpretar texto livre de município, ocupação e relato | Só o backend |
| ViaCEP e, de reserva, BrasilAPI | Buscar endereço pelo CEP | Só o backend |

Sem chave de IA o cadastro funciona por perguntas diretas. Sem acesso aos serviços de CEP o endereço é preenchido à mão. Detalhes em [integracao-ia.md](integracao-ia.md) e [integracoes-externas.md](integracoes-externas.md).

## Ferramentas de teste

JUnit 5, Spring Boot Test com MockMvc, Testcontainers (`postgres:16-alpine`), Mockito, MockWebServer 4.12.0. Ver [testes.md](testes.md).
