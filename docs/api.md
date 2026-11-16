# API

Todas as rotas ficam sob `/api/v1/cadastro` e dependem do cookie de sessão. **Não existe** listagem, busca por CPF nem rota que receba identificador de cadastro. São rotas da aplicação do desafio, não rotas oficiais da Defensoria.

## Convenções

| Item | Valor |
|---|---|
| Formato | JSON em UTF-8 |
| Sessão | Cookie `sessao_cadastro` (`HttpOnly`, `SameSite=Strict`, caminho `/api`), criado por `POST /sessao` |
| CSRF | Mutações (`POST`, `PATCH`, `DELETE`) exigem o cabeçalho `X-XSRF-TOKEN` com o valor do cookie `XSRF-TOKEN`. Uma chamada `GET /atual` entrega o cookie |
| Cache | Respostas sem cache (`Cache-Control` com `no-store`, dos cabeçalhos padrão do Spring Security) |
| Erros | `{"codigo": "...", "mensagem": "...", "campo": "..."}`, com `campo` só quando a resposta é recusada por causa de um campo. Sem pilha nem detalhe interno |
| Saúde | `GET /actuator/health` é o único endpoint de diagnóstico exposto |

## Rotas

| Método e rota | Função |
|---|---|
| `POST /sessao` | Inicia a sessão, define o cookie e devolve a abertura com a primeira pergunta (`201`) |
| `GET /atual` | Estado atual da sessão |
| `POST /respostas` | Responde à pergunta atual |
| `PATCH /campos/{campo}` | Corrige um campo já respondido |
| `GET /revisao` | Resumo por seção, pendências e a versão a revisar (só em `EM_REVISAO`) |
| `POST /confirmacao` | Grava o cadastro e devolve o comprovante |
| `DELETE /sessao` | Encerra a sessão, remove o rascunho e apaga o cookie (`204`). Não apaga cadastro confirmado |

### `POST /respostas`

```json
{
  "mensagemId": "6f1d3a52-3c8e-4d52-9a7b-0e1b8c8a1d11",
  "versao": 4,
  "valor": "Goiânia",
  "opcao": null
}
```

Use `valor` para texto digitado ou `opcao` para o id de uma opção. `mensagemId` é um UUID novo por mensagem e o reenvio da mesma mensagem não é reaplicado. `versao` é a do estado em que a pergunta foi mostrada.

### `PATCH /campos/{campo}`

```json
{ "versao": 22, "valor": "(62) 99876-5432", "opcao": null }
```

O `{campo}` é o id (`telefone`, `cpf`, `municipio`…). Só campos já respondidos e visíveis no resumo podem ser corrigidos.

### `POST /confirmacao`

```json
{ "versaoRevisada": 22, "chaveIdempotencia": "0b8f1c0e-5f6a-4c53-9a7e-2f3d1f3c9a11" }
```

## Corpo de estado

`POST /sessao`, `GET /atual`, `POST /respostas`, `PATCH /campos/{campo}` e `POST /confirmacao` devolvem o mesmo formato (`EstadoConversa`). Exemplo durante o preenchimento, com dados fictícios:

```json
{
  "estado": "EM_PREENCHIMENTO",
  "modoInteracao": "ESTRUTURADO",
  "versao": 4,
  "proximaPergunta": {
    "campo": "telefone",
    "texto": "Qual número podemos usar?",
    "ajuda": "Informe com DDD. Pode ser um número compartilhado.",
    "tipoEntrada": "TELEFONE",
    "opcoes": [{ "id": "SEM_TELEFONE", "rotulo": "Não tenho telefone" }],
    "tamanhoMaximo": 20,
    "itens": []
  },
  "progresso": {
    "etapa": 3,
    "total": 5,
    "secao": "Contato",
    "secoes": ["Identificação", "Contato", "Localização", "Situação socioeconômica"]
  },
  "resumo": [
    {
      "titulo": "Identificação",
      "itens": [
        {
          "campo": "nome",
          "rotulo": "Nome",
          "valor": "Maria Fictícia da Silva",
          "situacao": "INFORMADO",
          "edicao": { "campo": "nome", "texto": "Como você se chama?", "tipoEntrada": "TEXTO", "opcoes": [], "tamanhoMaximo": 200, "ajuda": null }
        }
      ]
    }
  ],
  "pendencias": [],
  "avisos": [],
  "comprovante": null,
  "expiraEm": "2026-11-15T12:30:00Z",
  "minutosDeInatividade": 30
}
```

| Campo | Significado |
|---|---|
| `estado` | `EM_PREENCHIMENTO`, `AGUARDANDO_ESCLARECIMENTO`, `EM_REVISAO`, `CONFIRMADO`, `CANCELADO` ou `EXPIRADO` |
| `modoInteracao` | `ESTRUTURADO` ou `ASSISTIDO_POR_IA` |
| `versao` | Versão do rascunho. Cada alteração aceita a incrementa |
| `proximaPergunta` | Texto, ajuda, tipo de entrada, opções e, na confirmação de interpretação ou de endereço, a lista `itens` com os dados entendidos. Nulo em revisão e depois da confirmação |
| `tipoEntrada` | `ESCOLHA`, `LISTA`, `TEXTO`, `RELATO`, `DATA`, `CPF`, `TELEFONE`, `EMAIL`, `CEP`, `VALOR` ou `NUMERO` |
| `progresso` | Etapa atual e títulos das seções aplicáveis. Sem percentual |
| `resumo` | Respostas aceitas por seção. `situacao` é `INFORMADO`, `NAO_INFORMADO` ou `OPCIONAL_NAO_PREENCHIDO`. `edicao` descreve a pergunta para corrigir o item |
| `pendencias` | Textos como `Data de nascimento: Não soube informar` |
| `avisos` | Mensagens do sistema para este turno (por exemplo IA indisponível ou CEP não encontrado) |
| `comprovante` | Só em `CONFIRMADO`: `identificador`, `confirmadoEm` e `comPendencias` |

### Esclarecimento pendente

Em `AGUARDANDO_ESCLARECIMENTO`, `proximaPergunta` é uma escolha entre `CONFIRMAR` e `MANTER`, com o texto adequado ao caso. Responder com `opcao: "CONFIRMAR"` aplica a alteração proposta, e `"MANTER"` a descarta. Qualquer outra opção é recusada.

### `GET /revisao`

```json
{ "versao": 22, "resumo": [ ... ], "pendencias": ["Data de nascimento: Não soube informar"] }
```

## Códigos de erro

| HTTP | `codigo` | Quando |
|---|---|---|
| 400 | `RESPOSTA_INVALIDA` | Resposta recusada pelas regras do campo. Traz `campo` e uma mensagem que diz o que corrigir |
| 400 | `REQUISICAO_INVALIDA` | Corpo malformado ou campos obrigatórios ausentes |
| 401 | `SESSAO_INEXISTENTE` | Sem cookie de sessão válido |
| 403 | `REQUISICAO_RECUSADA` | Token CSRF ausente ou inválido |
| 409 | `VERSAO_DESATUALIZADA` | A `versao` enviada não é a atual |
| 409 | `ESTADO_INVALIDO` | A operação não cabe no estado atual (por exemplo confirmar antes da revisão) |
| 409 | `CADASTRO_CONFLITANTE` | Não foi possível gravar. Não revela outro cadastro |
| 410 | `SESSAO_EXPIRADA` | A sessão expirou. Iniciar novo cadastro |
| 429 | `LIMITE_EXCEDIDO` | Limite de uso, com `Retry-After` quando há espera |
| 500 | `ERRO_INTERNO` | Falha interna, sem detalhe |

A confirmação não devolve sucesso simulado: se a gravação falha, a resposta é um erro e nenhum comprovante é emitido.

## Limites de uso

Aplicados no backend (valores padrão, configuráveis):

| Limite | Padrão |
|---|---|
| Respostas por sessão | 60 |
| Respostas por minuto por sessão | 10 |
| Requisições por minuto por origem | 120 |
| Respostas simultâneas por sessão | 1 |

São hipóteses iniciais, não capacidade medida. Valem para uma única instância da aplicação.
