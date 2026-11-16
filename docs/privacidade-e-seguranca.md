# Privacidade e segurança

Este é um protótipo com **dados fictícios**. Os controles abaixo existem e são testados, mas isso não substitui a avaliação jurídica e de segurança que um uso real exigiria (ver [Limitações](#limitações)).

## Minimização

| Decisão | Efeito |
|---|---|
| Sem listagem, busca por CPF ou rota por identificador de cadastro | Quem conhece um id ou um CPF não consegue consultar nada |
| Comprovante sem dado pessoal | Mostra só o identificador da operação, a data e se há pendências. Não permite consultar o cadastro |
| Rascunho temporário | Apagado ao confirmar, cancelar ou expirar. Cancelar não apaga cadastro já confirmado |
| Relato livre não é guardado | Fica só o marcador `ENVIADO` no rascunho |
| `mensagem_processada` sem texto | Guarda apenas ids e versões |
| Evento técnico sem conteúdo | Tipo, status, modo, modelo e duração |
| Conversa só em memória no navegador | Sem `localStorage` nem `sessionStorage`. Recarregar a página perde a conversa |

## O que sai do servidor

| Destino | O que é enviado | Quando |
|---|---|---|
| Provedor de IA | Texto livre do turno (município, ocupação, relato ou dúvida), pergunta atual, campos permitidos e títulos do catálogo de ajuda | Só no modo assistido por IA, depois de a pessoa aceitar |
| ViaCEP / BrasilAPI | Apenas o CEP, na URL | Quando a pessoa informa um CEP válido |

Nome, CPF, nascimento, telefone, e-mail, endereço detalhado e histórico da conversa **nunca** são enviados à IA. Texto com CPF, telefone ou e-mail escrito no lugar errado é recusado antes da chamada. Os filtros reduzem a exposição mas não garantem anonimização. Detalhes em [integracao-ia.md](integracao-ia.md) e [integracoes-externas.md](integracoes-externas.md).

## Sessão

- O segredo da sessão tem 256 bits (32 bytes de `SecureRandom`) e vai só no cookie `sessao_cadastro`: `HttpOnly`, `SameSite=Strict`, caminho `/api` e `Secure` quando `SESSAO_COOKIE_SEGURO=true`.
- O banco guarda apenas o **hash SHA-256** do segredo (`segredo_hash`). Um vazamento do banco não entrega sessões utilizáveis.
- Toda operação resolve a sessão pelo cookie. Não há parâmetro que aponte para a sessão ou o cadastro de outra pessoa.
- Expira por inatividade (30 minutos por padrão, `SESSAO_EXPIRACAO_MINUTOS`). Uma rotina a cada 5 minutos expira e remove o que passou do prazo.
- Sessão expirada devolve `410` e a tela volta ao início sem mostrar dados antigos.

## Proteção da API

| Controle | Implementação |
|---|---|
| CSRF | Token em cookie `XSRF-TOKEN` (`SameSite=Strict`) e cabeçalho `X-XSRF-TOKEN` obrigatório nas mutações. Sem ele, `403` |
| CORS | Só a mesma origem por padrão. Origens extras apenas via `CORS_ORIGENS`. Métodos `GET`, `POST`, `PATCH`, `DELETE` e cabeçalhos `Content-Type` e `X-XSRF-TOKEN` |
| Sem cache | Respostas com `Cache-Control` `no-store` |
| Sem autenticação própria | Sem formulário de login, HTTP Basic nem logout do Spring Security. A autorização é a sessão |
| Texto seguro | O React escapa o texto. Mensagens digitadas aparecem como texto, nunca como HTML |
| Erros | Corpo `{codigo, mensagem}` sem pilha, sem mensagem interna e sem prompt |
| Diagnóstico | Só `GET /actuator/health`, sem detalhes |

## Limites de uso

Aplicados no backend: 60 respostas por sessão, 10 por minuto por sessão, 120 requisições por minuto por origem e uma resposta em andamento por sessão. Em excesso, `429`, com `Retry-After` quando há espera. O limite por origem só olha `X-Forwarded-For` quando `LIMITES_CONFIAR_EM_PROXY=true`, o que o Compose ativa por estar atrás do nginx. Exponha a API diretamente apenas com essa opção desligada.

Também limitam custo e abuso: saída da IA limitada (350 tokens), timeout de 15 s na IA e 3 s no CEP, e um relato de no máximo 400 caracteres.

## Segredos

- A chave de IA fica só no processo da API, em variável de ambiente. O `.env` está no `.gitignore` e o `.env.example` traz a variável **vazia**.
- Não existe variável `VITE_` com segredo, e a chave não aparece no pacote do frontend, nas respostas, nos logs nem em `toString` (testes: `SegredoForaDoNavegadorTest`, `PropriedadesLlmTest`, `LogsSemDadosPessoaisTest`).
- A senha padrão do banco no Compose serve só ao banco local de demonstração. Defina `DB_PASSWORD` fora disso.
- As portas do Compose são publicadas apenas em `127.0.0.1`.

## Logs

Os logs registram eventos (tipo, status, modo, duração) e o nome da regra violada quando a IA devolve algo fora do contrato. Não registram CPF, CEP, texto da pessoa, resposta do modelo nem chave. O teste `LogsSemDadosPessoaisTest` confere isso em um fluxo completo.

## Validação de entrada

Toda entrada é validada no servidor, mesmo quando o frontend também valida. Os campos de texto têm tamanho máximo e rejeitam caractere de controle. As regras de cada campo estão em [regras-de-negocio.md](regras-de-negocio.md).

## Limitações

- **Não há verificação de identidade.** Um CPF válido não comprova titularidade, e não há consulta à Receita.
- **Não há autenticação de cidadão** nem painel interno: não é possível recuperar, alterar ou consultar um cadastro gravado.
- **Retenção e política de dados** de cadastros gravados dependem de definição institucional, e a base legal do tratamento não foi avaliada.
- **Provedores externos** (IA e CEP) não tiveram contrato, retenção, localização do processamento nem transferência internacional avaliados.
- **Limites em memória**: valem por instância. Com várias instâncias seria preciso estado compartilhado.
- **Sem criptografia em repouso** configurada no banco do Compose, e a API fala HTTP atrás do nginx local. Em produção seria preciso HTTPS (`SESSAO_COOKIE_SEGURO=true`) e a proteção do banco do ambiente.
- Revisar, confirmar, tomar ciência de aviso e assinar são ações diferentes. O botão **Confirmar cadastro** não é assinatura nem declaração oficial de hipossuficiência.
