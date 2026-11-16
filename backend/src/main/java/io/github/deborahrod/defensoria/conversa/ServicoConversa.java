package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.cadastro.Campo;
import io.github.deborahrod.defensoria.cadastro.ConfirmacaoCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.Esclarecimento;
import io.github.deborahrod.defensoria.cadastro.EstadoSessao;
import io.github.deborahrod.defensoria.cadastro.ModoInteracao;
import io.github.deborahrod.defensoria.cadastro.MotivoAusencia;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastro;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.SessaoCadastro;
import io.github.deborahrod.defensoria.cadastro.SessaoCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.SessaoInexistenteException;
import io.github.deborahrod.defensoria.integracao.FalhaInterpretacaoException.Categoria;
import io.github.deborahrod.defensoria.integracao.Interpretacao;
import io.github.deborahrod.defensoria.integracao.NormalizacaoDeTexto;
import io.github.deborahrod.defensoria.integracao.PedidoInterpretacao;
import io.github.deborahrod.defensoria.integracao.PedidoInterpretacao.CampoPermitido;
import io.github.deborahrod.defensoria.integracao.PropriedadesLlm;
import io.github.deborahrod.defensoria.integracao.ResultadoInterpretacao;
import io.github.deborahrod.defensoria.integracao.ResultadoInterpretacao.Operacao;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Conduz a conversa: o servidor decide a próxima pergunta e quando a revisão começa. */
@Service
public class ServicoConversa {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    /** Marca que o relato foi enviado. O texto em si nunca é guardado. */
    static final String RELATO_ENVIADO = "ENVIADO";

    private static final int LIMITE_RELATO = 400;

    private static final String SAUDACAO =
            "Vou fazer algumas perguntas, uma de cada vez. Você poderá corrigir qualquer resposta "
                    + "e revisar tudo antes de confirmar.";

    static final String SEM_IA =
            "A assistência por IA não está configurada neste ambiente. O cadastro segue com perguntas diretas.";
    static final String IA_INDISPONIVEL =
            "A assistência por IA está indisponível no momento. Suas respostas foram preservadas e vamos "
                    + "continuar com perguntas diretas. Responda de novo à pergunta atual.";

    private final SessaoCadastroRepository sessoes;
    private final RascunhoCadastroRepository rascunhos;
    private final MensagemProcessadaRepository mensagens;
    private final ConfirmacaoCadastroRepository confirmacoes;
    private final RoteiroCadastro roteiro;
    private final MontadorEstado montador;
    private final AjudaCatalogo ajuda;
    private final PropriedadesLlm llm;
    private final Clock relogio;

    public ServicoConversa(
            SessaoCadastroRepository sessoes,
            RascunhoCadastroRepository rascunhos,
            MensagemProcessadaRepository mensagens,
            ConfirmacaoCadastroRepository confirmacoes,
            RoteiroCadastro roteiro,
            MontadorEstado montador,
            AjudaCatalogo ajuda,
            PropriedadesLlm llm,
            Clock relogio) {
        this.sessoes = sessoes;
        this.rascunhos = rascunhos;
        this.mensagens = mensagens;
        this.confirmacoes = confirmacoes;
        this.roteiro = roteiro;
        this.montador = montador;
        this.ajuda = ajuda;
        this.llm = llm;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public EstadoConversa abertura(UUID sessaoId) {
        var avisos = llm.disponivel() ? List.of(SAUDACAO) : List.of(SAUDACAO, SEM_IA);
        return montador.montar(sessao(sessaoId), rascunho(sessaoId), avisos);
    }

    @Transactional(readOnly = true)
    public EstadoConversa estadoAtual(UUID sessaoId) {
        var sessao = sessao(sessaoId);
        if (sessao.getEstado() == EstadoSessao.CONFIRMADO) {
            return montador.montarConfirmado(sessao, confirmacoes.findBySessaoId(sessaoId).orElseThrow());
        }
        return montador.montar(sessao, rascunho(sessaoId), List.of());
    }

    @Transactional
    public EstadoConversa responder(UUID sessaoId, UUID mensagemId, int versao, RespostaEntrada entrada) {
        return responder(sessaoId, mensagemId, versao, entrada, Optional.empty());
    }

    /**
     * Decide se esta resposta vai à IA e, se for, devolve o mínimo necessário. O chamador faz a chamada
     * fora de transação. Vai à IA: o relato, o texto livre de município e ocupação e, em qualquer outra
     * pergunta, uma dúvida que a validação do campo recusou. Nunca vai texto com dado pessoal.
     */
    @Transactional(readOnly = true)
    public Optional<PedidoInterpretacao> prepararInterpretacao(
            UUID sessaoId, UUID mensagemId, int versao, RespostaEntrada entrada) {
        var sessao = sessao(sessaoId);
        if (sessao.getModoInteracao() != ModoInteracao.ASSISTIDO_POR_IA
                || sessao.getEstado() != EstadoSessao.EM_PREENCHIMENTO
                || entrada.opcao() != null
                || entrada.valor() == null
                || entrada.valor().isBlank()
                || mensagens.existsById(new MensagemProcessada.Chave(sessaoId, mensagemId))) {
            return Optional.empty();
        }
        var rascunho = rascunho(sessaoId);
        var proxima = roteiro.proxima(rascunho);
        if (versao != rascunho.getVersao() || proxima.isEmpty()) {
            return Optional.empty();
        }
        var pergunta = proxima.get();
        var texto = entrada.valor().strip();
        var relato = pergunta.campo() == Campo.RELATO;
        var extracao = relato || pergunta.interpretavelPorIa();

        if (!extracao && !pareceDuvidaRecusada(pergunta, rascunho, entrada, texto)) {
            return Optional.empty();
        }
        if (ContemDadosPessoais.em(texto)) {
            if (!extracao) {
                return Optional.empty();
            }
            throw new RespostaInvalidaException(pergunta.campo(),
                    "Não escreva CPF, telefone ou e-mail nesta resposta: esses dados são pedidos em campos próprios. "
                            + "Responda só à pergunta.");
        }
        if (relato) {
            if (texto.length() > LIMITE_RELATO) {
                throw new RespostaInvalidaException(pergunta.campo(), "O relato pode ter até 400 caracteres.");
            }
            if (ConteudoSensivel.em(texto)) {
                throw new RespostaInvalidaException(pergunta.campo(),
                        "Para sua segurança, não conte detalhes do seu caso aqui. Escreva só onde mora, o que faz "
                                + "e quem mora com você, ou responda pergunta por pergunta.");
            }
        }

        var campos = extracao
                ? camposDoTurno(rascunho, pergunta).stream()
                        .map(p -> new CampoPermitido(
                                p.campo().id(),
                                p.descricaoIa(),
                                p.escolhaFechada() ? p.idsDeOpcoes(rascunho) : List.of()))
                        .toList()
                : List.<CampoPermitido>of();
        return Optional.of(new PedidoInterpretacao(
                pergunta.texto(RoteiroCadastro.paraOutraPessoa(rascunho)), texto, campos, ajuda.topicos()));
    }

    @Transactional
    public EstadoConversa responder(
            UUID sessaoId, UUID mensagemId, int versao, RespostaEntrada entrada, Optional<Interpretacao> interpretacao) {
        var sessao = sessao(sessaoId);
        var rascunho = rascunho(sessaoId);
        // Reenvio da mesma mensagem: devolve o estado atual sem aplicar de novo.
        if (mensagens.existsById(new MensagemProcessada.Chave(sessaoId, mensagemId))) {
            return montador.montar(sessao, rascunho, List.of());
        }
        exigirVersaoAtual(rascunho, versao);
        var agora = relogio.instant();

        var avisos = List.<String>of();
        switch (sessao.getEstado()) {
            case EM_PREENCHIMENTO -> avisos = responderPergunta(sessao, rascunho, entrada, interpretacao, agora);
            case AGUARDANDO_ESCLARECIMENTO -> responderEsclarecimento(rascunho, entrada, agora);
            default -> throw new EstadoInvalidoException("Não há pergunta aguardando resposta.");
        }

        atualizarEstado(sessao, rascunho);
        rascunhos.flush();
        mensagens.save(new MensagemProcessada(sessaoId, mensagemId, versao, rascunho.getVersao(), agora));
        return montador.montar(sessao, rascunho, avisos);
    }

    /** Corrige um campo já respondido, preservando os demais e gerando nova versão. */
    @Transactional
    public EstadoConversa corrigir(UUID sessaoId, String campoId, int versao, RespostaEntrada entrada) {
        var sessao = sessao(sessaoId);
        var rascunho = rascunho(sessaoId);
        exigirVersaoAtual(rascunho, versao);
        if (sessao.getEstado() == EstadoSessao.AGUARDANDO_ESCLARECIMENTO) {
            throw new EstadoInvalidoException("Responda à pergunta em andamento antes de corrigir outro item.");
        }
        var campo = Campo.deId(campoId)
                .filter(rascunho::respondido)
                .orElseThrow(() -> new RespostaInvalidaException(campoId, "Este item não pode ser corrigido agora."));
        var pergunta = roteiro.de(campo, rascunho)
                .filter(Pergunta::noResumo)
                .orElseThrow(() -> new RespostaInvalidaException(campoId, "Este item não pode ser corrigido agora."));
        var agora = relogio.instant();
        var resolucao = pergunta.resolver(entrada, contexto(rascunho, agora));
        if (resolucao instanceof Resolucao.Invalida invalida) {
            throw new RespostaInvalidaException(campo, invalida.mensagem());
        }

        var simulado = rascunho.copia();
        aplicar(simulado, campo, resolucao, agora);
        var afetados = removerInvalidados(simulado, agora);
        var perderiaValor = afetados.stream().anyMatch(afetado -> rascunho.valor(afetado).isPresent());
        if (perderiaValor) {
            // Apagar um dado informado exige a concordância da pessoa.
            rascunho.pedirEsclarecimento(limpezaPendente(campo, resolucao, afetados), agora);
        } else {
            aplicar(rascunho, campo, resolucao, agora);
            afetados.forEach(afetado -> rascunho.limpar(afetado, agora));
        }

        atualizarEstado(sessao, rascunho);
        rascunhos.flush();
        return montador.montar(sessao, rascunho, List.of());
    }

    @Transactional(readOnly = true)
    public Revisao revisao(UUID sessaoId) {
        var sessao = sessao(sessaoId);
        if (sessao.getEstado() != EstadoSessao.EM_REVISAO) {
            throw new EstadoInvalidoException("A revisão fica disponível depois que todas as perguntas forem respondidas.");
        }
        var rascunho = rascunho(sessaoId);
        return new Revisao(rascunho.getVersao(), montador.resumo(rascunho), montador.pendencias(rascunho));
    }

    private List<String> responderPergunta(
            SessaoCadastro sessao,
            RascunhoCadastro rascunho,
            RespostaEntrada entrada,
            Optional<Interpretacao> interpretacao,
            Instant agora) {
        var pergunta = roteiro.proxima(rascunho)
                .orElseThrow(() -> new EstadoInvalidoException("Não há pergunta aguardando resposta."));
        var relato = pergunta.campo() == Campo.RELATO;
        if (interpretacao.isPresent() && entrada.valor() != null) {
            return relato || pergunta.interpretavelPorIa()
                    ? aplicarInterpretacao(sessao, rascunho, pergunta, entrada.valor().strip(), interpretacao.get(), agora)
                    : aplicarAjuda(sessao, rascunho, pergunta, entrada, interpretacao.get(), agora);
        }
        if (relato && entrada.valor() != null) {
            // O texto do relato só é aceito depois de interpretado: nunca vira valor do rascunho.
            throw new RespostaInvalidaException(pergunta.campo(),
                    "Não consegui interpretar o relato agora. Responda pergunta por pergunta.");
        }
        var resolucao = pergunta.resolver(entrada, contexto(rascunho, agora));
        if (resolucao instanceof Resolucao.Invalida invalida) {
            throw new RespostaInvalidaException(pergunta.campo(), invalida.mensagem());
        }
        aplicar(rascunho, pergunta.campo(), resolucao, agora);
        if (pergunta.campo() == Campo.AVISO_IA && rascunho.tem(Campo.AVISO_IA, RoteiroCadastro.USAR_IA)) {
            sessao.usarModo(ModoInteracao.ASSISTIDO_POR_IA);
        }
        // Valores trazidos pelo relato podem deixar de valer com a resposta nova (moradia e endereço fixo).
        removerInvalidados(rascunho, agora);
        return List.of();
    }

    /**
     * A IA só propõe. Cada candidato passa pelas regras do campo, nada sobrescreve resposta existente e
     * qualquer dúvida volta para a pessoa. Falha da IA muda o modo de coleta e preserva o rascunho.
     */
    private List<String> aplicarInterpretacao(
            SessaoCadastro sessao,
            RascunhoCadastro rascunho,
            Pergunta pergunta,
            String texto,
            Interpretacao interpretacao,
            Instant agora) {
        if (interpretacao instanceof Interpretacao.Falha) {
            return falhaDaIa(sessao, rascunho, pergunta, agora);
        }
        var resultado = ((Interpretacao.Sucesso) interpretacao).resultado();
        if (resultado.operacao() == Operacao.FORA_DE_ESCOPO) {
            throw new RespostaInvalidaException(pergunta.campo(),
                    "Posso ajudar apenas com o preenchimento deste cadastro. "
                            + pergunta.texto(RoteiroCadastro.paraOutraPessoa(rascunho)));
        }
        if (resultado.operacao() == Operacao.AJUDA) {
            return List.of(verbeteOuErro(resultado, pergunta, rascunho));
        }

        var aceitos = resolverCandidatos(rascunho, pergunta, resultado, agora);
        if (resultado.operacao() != Operacao.PROPOR_VALORES || resultado.precisaEsclarecimento() || aceitos.isEmpty()) {
            throw naoEntendi(pergunta, rascunho);
        }
        var relato = pergunta.campo() == Campo.RELATO;
        var campoAtual = pergunta.campo().id();
        // Só se aplica direto o que a pessoa digitou de fato: qualquer ajuste do modelo pede confirmação.
        if (!relato && aceitos.size() == 1 && aceitos.containsKey(campoAtual)
                && NormalizacaoDeTexto.normalizar(aceitos.get(campoAtual)).equals(NormalizacaoDeTexto.normalizar(texto))) {
            rascunho.informar(pergunta.campo(), aceitos.get(campoAtual), agora);
            return List.of();
        }
        var valores = new LinkedHashMap<>(aceitos);
        if (relato) {
            valores.put(Campo.RELATO.id(), RELATO_ENVIADO);
        }
        rascunho.pedirEsclarecimento(
                new Esclarecimento(Esclarecimento.Tipo.CONFIRMAR_INTERPRETACAO, valores, Map.of(), List.of()), agora);
        return List.of();
    }

    /** Dúvida em pergunta sem extração: só o verbete do catálogo responde. Qualquer outra coisa volta como erro do campo. */
    private List<String> aplicarAjuda(
            SessaoCadastro sessao,
            RascunhoCadastro rascunho,
            Pergunta pergunta,
            RespostaEntrada entrada,
            Interpretacao interpretacao,
            Instant agora) {
        if (interpretacao instanceof Interpretacao.Falha falha) {
            // Resposta fora do contrato é falha do modelo, não do provedor: mostra o erro do campo e segue.
            if (falha.categoria() == Categoria.RESPOSTA_INVALIDA) {
                throw erroDoCampo(pergunta, rascunho, entrada, agora);
            }
            return falhaDaIa(sessao, rascunho, pergunta, agora);
        }
        var resultado = ((Interpretacao.Sucesso) interpretacao).resultado();
        var verbete = resultado.operacao() == Operacao.AJUDA ? ajuda.texto(resultado.topico()) : Optional.<String>empty();
        if (verbete.isEmpty()) {
            throw erroDoCampo(pergunta, rascunho, entrada, agora);
        }
        return List.of("Ajuda: " + verbete.get());
    }

    private List<String> falhaDaIa(SessaoCadastro sessao, RascunhoCadastro rascunho, Pergunta pergunta, Instant agora) {
        sessao.usarModo(ModoInteracao.ESTRUTURADO);
        if (pergunta.campo() == Campo.RELATO) {
            rascunho.registrarAusencia(Campo.RELATO, MotivoAusencia.OPCIONAL_NAO_PREENCHIDO, agora);
        }
        return List.of(IA_INDISPONIVEL);
    }

    /** Valida os candidatos na ordem do roteiro, sobre uma cópia, para que um valide o seguinte. */
    private Map<String, String> resolverCandidatos(
            RascunhoCadastro rascunho, Pergunta atual, ResultadoInterpretacao resultado, Instant agora) {
        var copia = rascunho.copia();
        var aceitos = new LinkedHashMap<String, String>();
        for (var pergunta : camposDoTurno(rascunho, atual)) {
            var candidato = resultado.candidatos().stream()
                    .filter(c -> c.campo().equals(pergunta.campo().id()))
                    .findFirst();
            if (candidato.isEmpty() || copia.respondido(pergunta.campo()) || !pergunta.aplicavel(copia)) {
                continue;
            }
            var entrada = pergunta.escolhaFechada()
                    ? RespostaEntrada.opcao(candidato.get().valor())
                    : RespostaEntrada.valor(candidato.get().valor());
            if (pergunta.resolver(entrada, contexto(copia, agora)) instanceof Resolucao.Valor valor) {
                copia.informar(pergunta.campo(), valor.valor(), agora);
                aceitos.put(pergunta.campo().id(), valor.valor());
            }
        }
        return aceitos;
    }

    /** Perguntas que a IA pode preencher neste turno: as do relato, ou as de texto livre ainda abertas. */
    private List<Pergunta> camposDoTurno(RascunhoCadastro rascunho, Pergunta atual) {
        var relato = atual.campo() == Campo.RELATO;
        return roteiro.todas()
                .filter(p -> !rascunho.respondido(p.campo()))
                .filter(p -> relato
                        ? p.participaDoRelato()
                        : (p.interpretavelPorIa() && p.aplicavel(rascunho))
                                || p.sugeridaCom().filter(campo -> campo == atual.campo()).isPresent())
                .toList();
    }

    /** Resposta que o campo recusou e que parece uma pergunta, não uma tentativa de resposta. */
    private boolean pareceDuvidaRecusada(
            Pergunta pergunta, RascunhoCadastro rascunho, RespostaEntrada entrada, String texto) {
        var duvida = texto.contains("?") || texto.split("\\s+").length >= 2;
        return duvida && pergunta.resolver(entrada, contexto(rascunho, relogio.instant())) instanceof Resolucao.Invalida;
    }

    private RespostaInvalidaException erroDoCampo(
            Pergunta pergunta, RascunhoCadastro rascunho, RespostaEntrada entrada, Instant agora) {
        var mensagem = pergunta.resolver(entrada, contexto(rascunho, agora)) instanceof Resolucao.Invalida invalida
                ? invalida.mensagem()
                : "Não consegui usar essa resposta. Responda de forma direta.";
        return new RespostaInvalidaException(pergunta.campo(), mensagem);
    }

    private String verbeteOuErro(ResultadoInterpretacao resultado, Pergunta pergunta, RascunhoCadastro rascunho) {
        return ajuda.texto(resultado.topico())
                .map(texto -> "Ajuda: " + texto)
                .orElseThrow(() -> naoEntendi(pergunta, rascunho));
    }

    private static RespostaInvalidaException naoEntendi(Pergunta pergunta, RascunhoCadastro rascunho) {
        var enunciado = pergunta.texto(RoteiroCadastro.paraOutraPessoa(rascunho));
        var mensagem = pergunta.campo() == Campo.RELATO
                ? "Não consegui identificar dados nesse texto. Escreva onde mora, o que faz e quem mora com você, "
                        + "ou escolha responder pergunta por pergunta."
                : "Não consegui entender essa resposta com segurança. Responda de forma direta: " + enunciado;
        return new RespostaInvalidaException(pergunta.campo(), mensagem);
    }

    private void responderEsclarecimento(RascunhoCadastro rascunho, RespostaEntrada entrada, Instant agora) {
        var pendente = rascunho.esclarecimento().orElseThrow();
        if (MontadorEstado.CONFIRMAR.equals(entrada.opcao())) {
            pendente.valores().forEach((id, valor) -> rascunho.informar(campo(id), valor, agora));
            pendente.ausencias().forEach(
                    (id, motivo) -> rascunho.registrarAusencia(campo(id), MotivoAusencia.valueOf(motivo), agora));
            pendente.afetados().forEach(id -> rascunho.limpar(campo(id), agora));
        } else if (!MontadorEstado.MANTER.equals(entrada.opcao())) {
            throw new RespostaInvalidaException((String) null, "Escolha uma das opções apresentadas.");
        }
        rascunho.encerrarEsclarecimento(agora);
    }

    /** Remove respostas que deixaram de se aplicar ou de valer e devolve quais foram. */
    private List<Campo> removerInvalidados(RascunhoCadastro rascunho, Instant agora) {
        var removidos = new ArrayList<Campo>();
        boolean mudou;
        do {
            mudou = false;
            for (var campo : Campo.values()) {
                if (rascunho.respondido(campo) && !continuaValido(campo, rascunho, agora)) {
                    rascunho.limpar(campo, agora);
                    removidos.add(campo);
                    mudou = true;
                }
            }
        } while (mudou);
        return removidos;
    }

    private boolean continuaValido(Campo campo, RascunhoCadastro rascunho, Instant agora) {
        var pergunta = roteiro.de(campo, rascunho);
        if (pergunta.isEmpty()) {
            return false;
        }
        var contexto = contexto(rascunho, agora);
        return rascunho.valor(campo)
                .map(valor -> pergunta.get().aindaAceita(valor, contexto))
                .orElseGet(() -> pergunta.get().aceitaAusencia(rascunho.ausencia(campo).orElseThrow(), rascunho));
    }

    private static void aplicar(RascunhoCadastro rascunho, Campo campo, Resolucao resolucao, Instant agora) {
        switch (resolucao) {
            case Resolucao.Valor aceito -> rascunho.informar(campo, aceito.valor(), agora);
            case Resolucao.Ausente ausente -> rascunho.registrarAusencia(campo, ausente.motivo(), agora);
            case Resolucao.Invalida invalida -> throw new IllegalArgumentException(invalida.mensagem());
        }
    }

    private static Esclarecimento limpezaPendente(Campo campo, Resolucao resolucao, List<Campo> afetados) {
        var valores = resolucao instanceof Resolucao.Valor aceito ? Map.of(campo.id(), aceito.valor()) : Map.<String, String>of();
        var ausencias = resolucao instanceof Resolucao.Ausente ausente
                ? Map.of(campo.id(), ausente.motivo().name())
                : Map.<String, String>of();
        return new Esclarecimento(
                Esclarecimento.Tipo.CONFIRMAR_LIMPEZA, valores, ausencias, afetados.stream().map(Campo::id).toList());
    }

    private void atualizarEstado(SessaoCadastro sessao, RascunhoCadastro rascunho) {
        if (rascunho.esclarecimento().isPresent()) {
            sessao.mudarPara(EstadoSessao.AGUARDANDO_ESCLARECIMENTO);
        } else if (roteiro.proxima(rascunho).isPresent()) {
            sessao.mudarPara(EstadoSessao.EM_PREENCHIMENTO);
        } else {
            sessao.mudarPara(EstadoSessao.EM_REVISAO);
        }
    }

    private static void exigirVersaoAtual(RascunhoCadastro rascunho, int versao) {
        if (versao != rascunho.getVersao()) {
            throw new VersaoDesatualizadaException();
        }
    }

    private static Campo campo(String id) {
        return Campo.deId(id).orElseThrow();
    }

    private SessaoCadastro sessao(UUID sessaoId) {
        return sessoes.findById(sessaoId).orElseThrow(SessaoInexistenteException::new);
    }

    private RascunhoCadastro rascunho(UUID sessaoId) {
        return rascunhos.findById(sessaoId)
                .orElseThrow(() -> new EstadoInvalidoException("Este cadastro não está mais em preenchimento."));
    }

    private static ContextoResposta contexto(RascunhoCadastro rascunho, Instant agora) {
        return new ContextoResposta(rascunho, LocalDate.ofInstant(agora, FUSO));
    }
}
