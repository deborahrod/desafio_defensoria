# Cadastro de pessoa assistida

Protótipo de cadastro de pessoa assistida por conversa guiada, desenvolvido para a Defensoria Pública do Estado de Goiás. A pessoa responde a uma pergunta por vez, confere um resumo editável e confirma o envio. O backend controla o fluxo, valida os dados e grava o cadastro.

É uma demonstração técnica com dados fictícios. Não tem validade oficial nem integração com sistemas da Defensoria.

## Estrutura

| Pasta | Conteúdo |
|---|---|
| `backend/` | API em Java 21 com Spring Boot 3 |
| `frontend/` | Interface em React, TypeScript e Vite |
| `docker-compose.yml` | Banco PostgreSQL, API e interface |

## Como rodar com Docker

Requer Docker com Compose.

```bash
cp .env.example .env
docker compose up --build
```

A interface fica em http://localhost:8080 e a API em http://localhost:8081. As portas são publicadas apenas em `127.0.0.1` e podem ser alteradas no `.env`.

`DB_PASSWORD` vazio usa uma senha padrão, adequada apenas ao banco local de demonstração.

Para parar e apagar os dados de demonstração:

```bash
docker compose down -v
```

## Como rodar localmente

Requer JDK 21 e Node 22 ou superior.

```bash
docker compose up -d db

cd backend
./mvnw spring-boot:run

cd frontend
npm install
npm run dev
```

O Vite serve a interface em http://localhost:5173 e encaminha `/api` para a API em `localhost:8081`.

## Testes

```bash
cd backend && ./mvnw verify
cd frontend && npm run lint && npm test
```
