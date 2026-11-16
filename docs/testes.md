# Testes

## Como rodar

```bash
cd backend && ./mvnw verify          # requer Docker (PostgreSQL via Testcontainers)
cd frontend && npm run lint && npm test && npm run build
```

No PowerShell, use `.\mvnw.cmd` no lugar de `./mvnw`.

Resultado da última execução completa: **backend com 396 testes, 4 ignorados** (os que dependem de chave real), e **frontend com 93 testes**, todos passando.

## Estratégia

| Camada | Como é testada |
|---|---|
| Regras puras (validadores, CPF, filtros, catálogo) | Testes unitários |
| API e fluxo completo | Spring Boot Test com MockMvc e PostgreSQL 16 real em contêiner (Testcontainers), o mesmo do Compose |
| IA | `InterpretadorFalso` programável nos testes de fluxo. `MockWebServer` nos testes do cliente HTTP |
| CEP | `ConsultaCepFalsa` nos testes de fluxo. `MockWebServer` (dois servidores) nos testes dos provedores |
| Relógio | `RelogioDeTeste` controlável, para expiração e competência |
| Frontend | Vitest com Testing Library, `fetch` simulado e axe-core |

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
| Outras | 23 | Chave de origem, limite por origem, catálogo de ajuda, operação única por sessão, falha na transação, logs sem dados pessoais, segredo fora do navegador, propriedades da IA, inicialização do aplicativo e `InterpretadorRealTest` (4, ignorados sem chave) |

### Cenários que merecem destaque

- **Falha na transação:** se a gravação da confirmação falha no meio, nada fica gravado e nenhum comprovante é emitido (`FalhaNaTransacaoTest`).
- **Corrida:** duas confirmações simultâneas gravam um único cadastro. Duas respostas simultâneas na mesma sessão recebem `429` na segunda (`OperacaoUnicaPorSessaoTest`).
- **Injeção de instruções:** respostas do modelo com campos extras, valores fora das opções ou trechos que não estão no texto são descartadas por inteiro.
- **Segredo:** a chave não aparece em respostas, logs, `toString` nem no pacote do frontend.

## Frontend

| Arquivo | O que cobre |
|---|---|
| `validacao.test.ts` | Máscara e validação de CPF e telefone, com os mesmos casos e mensagens do servidor |
| `EntradaConversa.test.tsx` | Máscara ao digitar, apagar atravessando símbolos, erro acessível e que some ao corrigir, ausência sem validar |
| `ChatCadastro.test.tsx` | Envio único, duplo clique, texto exibido como texto, sessão expirada, modo de IA |
| `RelatoEConfirmacao.test.tsx` | Relato, lista "Entendi assim" e ajuda |
| `PainelRespostas.test.tsx` | Painel durante o preenchimento, edição, bloqueio com escolha pendente |
| `TelaConferencia.test.tsx` | Conferência em tela cheia, selo de ausência, cancelamento, acessibilidade |
| `RevisaoCadastro.test.tsx` | Resumo, correção, recusa do servidor, confirmação, comprovante, falha de rede com a mesma chave, conflito e encerramento |
| `PaginaInicial.test.tsx` | Apresentação e aviso de protótipo |
| `Acessibilidade.test.tsx` | axe nas telas e jornada completa só com teclado |
| `Tema.test.ts` | Marca e cores do portal, sem cor solta nos estilos |
| `App.test.tsx` | Montagem do aplicativo |

O axe roda sem a regra de contraste, porque o jsdom não calcula estilos. O contraste das cores do tema foi calculado e todo texto passa de 4,5:1.

## Teste de chamada real à IA

`InterpretadorRealTest` chama o provedor configurado com texto fictício e só roda se `LLM_API_KEY` estiver no ambiente. Os demais testes esperam o ambiente **sem** chave, então carregue o `.env` apenas para esse teste:

```bash
set -a; . ./.env; set +a
cd backend && ./mvnw test -Dtest=InterpretadorRealTest
```

## O que não é coberto

- Não há suíte automatizada de navegador de ponta a ponta. A jornada completa foi conferida manualmente no navegador, contra a API real.
- Não há teste de carga nem medição de capacidade. Os limites de uso são hipóteses.
- Os testes de CEP usam servidores simulados. O formato real dos dois provedores foi conferido manualmente com um CEP público, mas sem teste automatizado contra a internet.
- O contraste de cores não é medido pelos testes automatizados.
