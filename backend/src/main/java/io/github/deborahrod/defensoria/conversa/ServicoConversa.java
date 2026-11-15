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
import io.github.deborahrod.defensoria.cadastro.SessaoInexistenteException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private final SessaoCadastroRepository sessoes;
    private final RascunhoCadastroRepository rascunhos;
    private final MensagemProcessadaRepository mensagens;
    private final ConfirmacaoCadastroRepository confirmacoes;
    private final RoteiroCadastro roteiro;
    private final MontadorEstado montador;
    private final Clock relogio;

    public ServicoConversa(
            SessaoCadastroRepository sessoes,
            RascunhoCadastroRepository rascunhos,
            MensagemProcessadaRepository mensagens,
            ConfirmacaoCadastroRepository confirmacoes,
            RoteiroCadastro roteiro,
            MontadorEstado montador,
            Clock relogio) {
        this.sessoes = sessoes;
        this.rascunhos = rascunhos;
        this.mensagens = mensagens;
        this.confirmacoes = confirmacoes;
        this.roteiro = roteiro;
        this.montador = montador;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public EstadoConversa abertura(UUID sessaoId) {
        return montador.montar(sessao(sessaoId), rascunho(sessaoId), List.of(SAUDACAO));
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
        var sessao = sessao(sessaoId);
        var rascunho = rascunho(sessaoId);
        // Reenvio da mesma mensagem: devolve o estado atual sem aplicar de novo.
        if (mensagens.existsById(new MensagemProcessada.Chave(sessaoId, mensagemId))) {
            return montador.montar(sessao, rascunho, List.of());
        }
        exigirVersaoAtual(rascunho, versao);
        var agora = relogio.instant();

        switch (sessao.getEstado()) {
            case EM_PREENCHIMENTO -> responderPergunta(rascunho, entrada, agora);
            case AGUARDANDO_ESCLARECIMENTO -> responderEsclarecimento(rascunho, entrada, agora);
            default -> throw new EstadoInvalidoException("Não há pergunta aguardando resposta.");
        }

        atualizarEstado(sessao, rascunho);
        rascunhos.flush();
        mensagens.save(new MensagemProcessada(sessaoId, mensagemId, versao, rascunho.getVersao(), agora));
        return montador.montar(sessao, rascunho, List.of());
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

    private void responderPergunta(RascunhoCadastro rascunho, RespostaEntrada entrada, Instant agora) {
        var pergunta = roteiro.proxima(rascunho)
                .orElseThrow(() -> new EstadoInvalidoException("Não há pergunta aguardando resposta."));
        var resolucao = pergunta.resolver(entrada, contexto(rascunho, agora));
        if (resolucao instanceof Resolucao.Invalida invalida) {
            throw new RespostaInvalidaException(pergunta.campo(), invalida.mensagem());
        }
        aplicar(rascunho, pergunta.campo(), resolucao, agora);
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
