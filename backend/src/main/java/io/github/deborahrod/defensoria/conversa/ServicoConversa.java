package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.cadastro.Campo;
import io.github.deborahrod.defensoria.cadastro.ConfirmacaoCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.Esclarecimento;
import io.github.deborahrod.defensoria.cadastro.EstadoSessao;
import io.github.deborahrod.defensoria.cadastro.MotivoAusencia;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastro;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.SessaoCadastro;
import io.github.deborahrod.defensoria.cadastro.SessaoCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.ModoInteracao;
import io.github.deborahrod.defensoria.cadastro.SessaoInexistenteException;
import io.github.deborahrod.defensoria.integracao.Interpretacao;
import io.github.deborahrod.defensoria.integracao.PedidoInterpretacao;
import io.github.deborahrod.defensoria.integracao.PropriedadesLlm;
import io.github.deborahrod.defensoria.integracao.ResultadoInterpretacao.Operacao;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Conduz a conversa: o servidor decide a próxima pergunta e quando a revisão começa. */
@Service
public class ServicoConversa {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

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
    private final PropriedadesLlm llm;
    private final Clock relogio;

    public ServicoConversa(
            SessaoCadastroRepository sessoes,
            RascunhoCadastroRepository rascunhos,
            MensagemProcessadaRepository mensagens,
            ConfirmacaoCadastroRepository confirmacoes,
            RoteiroCadastro roteiro,
            MontadorEstado montador,
            PropriedadesLlm llm,
            Clock relogio) {
        this.sessoes = sessoes;
        this.rascunhos = rascunhos;
        this.mensagens = mensagens;
        this.confirmacoes = confirmacoes;
        this.roteiro = roteiro;
        this.montador = montador;
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
     * Decide se esta resposta vai à IA: só texto livre elegível, com a assistência escolhida, sem dados pessoais.
     * Quando vai, devolve o mínimo necessário; o chamador faz a chamada fora de transação.
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
        var pergunta = roteiro.proxima(rascunho).filter(Pergunta::interpretavelPorIa);
        if (versao != rascunho.getVersao() || pergunta.isEmpty()) {
            return Optional.empty();
        }
        var texto = entrada.valor().strip();
        if (ContemDadosPessoais.em(texto)) {
            throw new RespostaInvalidaException(pergunta.get().campo(),
                    "Não escreva CPF, telefone ou e-mail nesta resposta: esses dados são pedidos em campos próprios. "
                            + "Responda só à pergunta.");
        }
        var campos = roteiro.aplicaveis(rascunho)
                .filter(Pergunta::interpretavelPorIa)
                .filter(p -> !rascunho.respondido(p.campo()))
                .map(p -> new PedidoInterpretacao.CampoPermitido(p.campo().id(), p.descricaoIa()))
                .toList();
        return Optional.of(new PedidoInterpretacao(
                pergunta.get().texto(RoteiroCadastro.paraOutraPessoa(rascunho)), texto, campos));
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
        if (interpretacao.isPresent() && pergunta.interpretavelPorIa() && entrada.valor() != null) {
            return aplicarInterpretacao(sessao, rascunho, pergunta, interpretacao.get(), agora);
        }
        var resolucao = pergunta.resolver(entrada, contexto(rascunho, agora));
        if (resolucao instanceof Resolucao.Invalida invalida) {
            throw new RespostaInvalidaException(pergunta.campo(), invalida.mensagem());
        }
        aplicar(rascunho, pergunta.campo(), resolucao, agora);
        if (pergunta.campo() == Campo.AVISO_IA && rascunho.tem(Campo.AVISO_IA, RoteiroCadastro.USAR_IA)) {
            sessao.usarModo(ModoInteracao.ASSISTIDO_POR_IA);
        }
        return List.of();
    }

    /**
     * A IA só propõe. Cada candidato passa pelas regras do campo, nada sobrescreve resposta existente e
     * qualquer dúvida volta para a pessoa. Falha da IA muda o modo de coleta e preserva o rascunho.
     */
    private List<String> aplicarInterpretacao(
            SessaoCadastro sessao, RascunhoCadastro rascunho, Pergunta pergunta, Interpretacao interpretacao, Instant agora) {
        if (interpretacao instanceof Interpretacao.Falha) {
            sessao.usarModo(ModoInteracao.ESTRUTURADO);
            return List.of(IA_INDISPONIVEL);
        }
        var resultado = ((Interpretacao.Sucesso) interpretacao).resultado();
        var enunciado = pergunta.texto(RoteiroCadastro.paraOutraPessoa(rascunho));
        if (resultado.operacao() == Operacao.FORA_DE_ESCOPO) {
            throw new RespostaInvalidaException(pergunta.campo(),
                    "Posso ajudar apenas com o preenchimento deste cadastro. " + enunciado);
        }

        var contexto = contexto(rascunho, agora);
        var aceitos = new LinkedHashMap<String, String>();
        for (var candidato : resultado.candidatos()) {
            var alvo = Campo.deId(candidato.campo())
                    .filter(campo -> !rascunho.respondido(campo))
                    .flatMap(campo -> roteiro.aplicaveis(rascunho)
                            .filter(p -> p.campo() == campo && p.interpretavelPorIa())
                            .findFirst());
            alvo.ifPresent(p -> {
                if (p.resolver(RespostaEntrada.valor(candidato.valor()), contexto) instanceof Resolucao.Valor valor) {
                    aceitos.putIfAbsent(p.campo().id(), valor.valor());
                }
            });
        }

        if (resultado.operacao() != Operacao.PROPOR_VALORES || resultado.precisaEsclarecimento() || aceitos.isEmpty()) {
            throw new RespostaInvalidaException(pergunta.campo(),
                    "Não consegui entender essa resposta com segurança. Responda de forma direta: " + enunciado);
        }
        if (aceitos.size() == 1 && aceitos.containsKey(pergunta.campo().id())) {
            rascunho.informar(pergunta.campo(), aceitos.get(pergunta.campo().id()), agora);
        } else {
            rascunho.pedirEsclarecimento(
                    new Esclarecimento(Esclarecimento.Tipo.CONFIRMAR_INTERPRETACAO, aceitos, Map.of(), List.of()), agora);
        }
        return List.of();
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
