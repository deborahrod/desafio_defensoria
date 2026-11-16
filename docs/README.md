# Documentação

Esta pasta descreve o que o código **faz hoje**. Nada aqui é plano futuro: o que não foi implementado aparece como limitação em cada documento e no [README da raiz](../README.md).

O projeto é uma demonstração técnica de cadastro de pessoa assistida por conversa guiada, feita para a Defensoria Pública do Estado de Goiás, com dados fictícios. Os requisitos vêm da ata de requisitos do desafio, citados como RF01 a RF18.

## Por onde começar

| Quero entender… | Leia |
|---|---|
| Com o que o sistema foi feito e por quê | [tecnologias.md](tecnologias.md) |
| Como as partes se conversam e como um turno é processado | [arquitetura.md](arquitetura.md) |
| O que o sistema decide, estados, ausências, duplicidade, idempotência | [regras-de-negocio.md](regras-de-negocio.md) |
| Tabelas, restrições e o que fica no rascunho | [modelo-de-dados.md](modelo-de-dados.md) |
| Rotas, corpos de requisição e resposta, erros | [api.md](api.md) |
| O que a IA faz, o que recebe e como é contida | [integracao-ia.md](integracao-ia.md) |
| Como o endereço é buscado pelo CEP | [integracoes-externas.md](integracoes-externas.md) |
| Dados pessoais, sessão, segredos e limites de uso | [privacidade-e-seguranca.md](privacidade-e-seguranca.md) |
| Como rodar e o que os testes cobrem | [testes.md](testes.md) |
| Por que cada escolha foi feita | [decisoes.md](decisoes.md) |

## Mapa rápido do código

```
backend/src/main/java/io/github/deborahrod/defensoria/
  cadastro/       sessão, rascunho versionado, entidades gravadas
  conversa/       roteiro de perguntas, validadores, API REST, limites, confirmação
  integracao/     interpretador de IA, consulta de CEP, eventos técnicos
  configuracao/   segurança, CSRF, CORS, tratamento de erros
frontend/src/
  componentes/    telas e entradas (página inicial, conversa, conferência, comprovante)
  validacao/      máscara e validação de CPF e telefone
  api/            cliente HTTP e tipos
  conversa/       hook que orquestra a conversa
  tema/           tokens visuais da identidade da Defensoria
```

## Como ler os diagramas

Os diagramas usam Mermaid e aparecem renderizados no GitHub. Em outros visualizadores, o texto do bloco continua legível.
