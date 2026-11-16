# Decisões

Registro curto das escolhas que mais moldaram o projeto. Cada uma diz o contexto, o que foi decidido e o que isso custa.

## D01. Adaptador compatível com Chat Completions, com Mistral como padrão

- **Contexto.** A ata prevê Qwen via Hugging Face Inference Providers.
- **Decisão.** Um adaptador para qualquer API compatível com Chat Completions, com Mistral como padrão. Contrato de saída, validações, fallback e privacidade da ata foram mantidos. Gemini também foi validado.
- **Consequência.** Trocar de provedor é mudar URL, modelo e chave, sem tocar no código, e o Hugging Face continua possível. O custo é depender de que o endpoint aceite autenticação Bearer e `response_format` JSON.

## D02. A IA propõe e o Java decide

- **Contexto.** RF02, RF05 e RF08 pedem que a interface e o modelo não mudem obrigatoriedades nem gravem.
- **Decisão.** O contrato da IA só tem operações de proposta. Toda saída é validada de forma estrita, cada candidato passa de novo pelas regras do campo e qualquer violação descarta a resposta inteira. A IA nunca grava, confirma nem escolhe a próxima pergunta.
- **Consequência.** Respostas do modelo com um detalhe fora do contrato são descartadas em vez de aproveitadas em parte. Isso custa algumas chamadas "perdidas", em troca de nunca aplicar valor duvidoso.

## D03. Roteiro declarativo e próxima pergunta derivada

- **Decisão.** `RoteiroCadastro` é uma lista de `Pergunta` com condição de aplicação. A etapa atual não é guardada: é a primeira pergunta aplicável sem resposta.
- **Consequência.** Corrigir uma resposta que muda a aplicabilidade de outras (por exemplo, trocar "tem endereço fixo") mantém o fluxo coerente sem lógica de migração de estado. O custo é recalcular a cada turno, o que é barato com cerca de 30 perguntas.

## D04. Sessão própria em vez de `HttpSession`

- **Decisão.** Segredo aleatório de 256 bits em cookie `HttpOnly` e `SameSite=Strict`, com só o hash no banco.
- **Consequência.** Dá para expirar e limpar sessões por consulta e para ligar o rascunho à sessão no banco. Em troca, o código de sessão é nosso e precisa dos testes que ele tem.

## D05. `AJUDA` estende o contrato da ata

- **Contexto.** Uma dúvida durante o preenchimento ("o que é dependente?") merece resposta, mas a ata proíbe aconselhamento do modelo.
- **Decisão.** Nova operação `AJUDA`, em que o modelo devolve **só o id** de um verbete do catálogo local. O texto exibido é sempre escrito por nós.
- **Consequência.** Há ajuda contextual sem que o modelo escreva conteúdo para a pessoa. A extensão está registrada aqui e no README.

## D06. Aplicar direto só o que a pessoa digitou

- **Decisão.** Um valor vindo da IA só entra direto se for igual ao digitado (sem considerar acento e caixa) e for um único campo. Normalização, UF sugerida ou mais de um dado passam por "Entendi assim".
- **Consequência.** Há uma confirmação a mais em alguns casos, mas a pessoa nunca descobre depois que o modelo reescreveu uma resposta dela.

## D07. O relato livre não é guardado

- **Decisão.** O texto vai à IA, vira propostas e é descartado. No rascunho fica só `ENVIADO`. Limite de 400 caracteres, filtro de dados pessoais e de detalhes do caso.
- **Consequência.** Menos dado sensível parado. Em contrapartida, não dá para reprocessar o relato depois, e o limite é menor que o proposto na ata.

## D08. A chamada externa fica fora da transação

- **Decisão.** `ServicoResposta` consulta IA e CEP entre duas transações curtas e a gravação revalida a versão.
- **Consequência.** Não segura conexão do banco esperando terceiros. Se o estado mudar durante a espera, o turno é recusado e a tela recarrega.

## D09. Consulta de CEP no backend, com reserva e confirmação

- **Decisão.** O backend consulta o ViaCEP e, se falhar ou não conhecer, a BrasilAPI. Timeout curto, cache em memória e confirmação do endereço pela pessoa. Falha nunca bloqueia: o endereço segue manual.
- **Consequência.** O navegador não fala com terceiros e só o CEP sai. Há dependência de serviços públicos sem garantia de disponibilidade, mitigada pelo fallback e pelo modo manual.

## D10. O CEP vem antes do logradouro

- **Decisão.** A pergunta de CEP é a primeira do bloco de endereço fixo.
- **Consequência.** Quando o CEP é achado, rua e bairro não são perguntados. Quando é recusado ou não existe, o fluxo cai no preenchimento manual sem perder nada.

## D11. Conferência em tela própria

- **Contexto.** A conferência dentro da conversa misturava revisão com mensagens e deixava o botão de confirmar perto de uma caixa de texto.
- **Decisão.** Ao fim das perguntas, a conversa e o painel saem de cena. A `TelaConferencia` mostra cartões por seção, destaca ausências com selo de texto, oferece **Corrigir** por item e concentra o único botão **Confirmar cadastro**. O comprovante também tem tela própria.
- **Consequência.** Uma correção que reabre perguntas devolve a pessoa à conversa automaticamente. O painel "Suas respostas" continua só durante o preenchimento.

## D12. Validação de CPF e telefone nos dois lados

- **Decisão.** O frontend aplica máscara e as mesmas regras e mensagens para resposta imediata. O servidor continua a fonte de verdade.
- **Consequência.** Há duas implementações da regra, mantidas alinhadas por um arquivo único de casos (`testes-compartilhados/validacao.json`) que os testes dos dois lados leem: se divergirem, um deles falha. Um cliente que pule a interface continua sendo barrado.

## D13. Material UI só onde ajuda

- **Decisão.** Só o `Stepper` vem do Material UI, com tema próprio derivado dos tokens. Cartões, conversa, painel e formulários são CSS Modules com os mesmos tokens.
- **Consequência.** Pacote pequeno e identidade da Defensoria sem sobrescrever o tema padrão de uma biblioteca. A ata exige que o tema padrão não apareça.

## D14. Rascunho em `JSONB`

- **Decisão.** Dados, ausências e esclarecimento pendente ficam em colunas `JSONB` de uma linha por sessão, versionada.
- **Consequência.** Adicionar uma pergunta não exige migração do rascunho. Os dados definitivos, em contrapartida, são colunas tipadas com restrições, onde o esquema importa.

## D15. Limites de uso em memória

- **Decisão.** Os limites por sessão, por minuto e por origem vivem na memória da instância.
- **Consequência.** Simples e suficiente para uma instância. Com várias, seria preciso estado compartilhado. Os números são hipóteses iniciais, não capacidade medida.

## D16. O proxy define o endereço de origem

- **Contexto.** O limite por origem usava a primeira entrada de `X-Forwarded-For`, e o nginx anexava o que o cliente mandasse, então um cabeçalho forjado dava um balde novo a cada requisição.
- **Decisão.** O nginx **substitui** o cabeçalho pelo endereço da conexão e a API usa a última entrada da cadeia. O mapa de origens acompanhadas tem tamanho máximo.
- **Consequência.** Não dá mais para escapar do teto forjando o cabeçalho. Atrás de outro proxy ou balanceador, é preciso configurá-lo para repassar o endereço do cliente.

## D17. Perfil de produção que falha cedo

- **Decisão.** Com `SPRING_PROFILES_ACTIVE=prod` a API não sobe com cookie sem `Secure` nem com a senha de banco padrão.
- **Consequência.** Esquecer uma configuração de segurança vira erro na subida, não um risco silencioso. Os padrões de demonstração continuam servindo ao uso local.

## D18. Política de conteúdo restritiva no frontend

- **Decisão.** O nginx envia uma CSP que só aceita a própria origem, sem script inline nem `eval`. As fontes Roboto são empacotadas e o único afrouxamento é `'unsafe-inline'` em estilos, porque o Material UI injeta `<style>`.
- **Consequência.** Reduz o impacto de uma injeção de HTML. Trocar o Material UI por CSS próprio, ou usar nonce, permitiria remover o afrouxamento.

## D19. CI com teste de navegador contra o Compose

- **Contexto.** Uma falha real, o `X-Forwarded-For` forjado, passou pelos testes porque eles verificavam a função e não o caminho completo pelo nginx. Alguns commits intermediários também ficaram sem passar isolados.
- **Decisão.** Um fluxo de CI com três jobs (backend, frontend e navegador). O de navegador sobe o Compose de verdade, percorre a jornada pela interface e ataca a borda como um cliente hostil. A regressão do cabeçalho forjado foi verificada para falhar com a configuração antiga.
- **Consequência.** Erros de integração entre nginx, API e interface aparecem a cada push. Em troca, o job leva alguns minutos a mais e o fluxo de CEP real fica de fora, por depender da internet.

## D20. A conversa não é persistida no navegador

- **Decisão.** O estado vive só em memória, sem `localStorage`.
- **Consequência.** Menos dado pessoal no dispositivo. Recarregar a página perde a conversa e **Começar** abre uma nova sessão.
