package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.cadastro.EstadoSessao;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastro;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.SessaoCadastro;
import io.github.deborahrod.defensoria.cadastro.SessaoCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.SessaoInexistenteException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Conduz a conversa: o servidor decide a próxima pergunta e quando a revisão começa. */
@Service
public class ServicoConversa {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final SessaoCadastroRepository sessoes;
    private final RascunhoCadastroRepository rascunhos;
    private final MensagemProcessadaRepository mensagens;
    private final RoteiroCadastro roteiro;
    private final MontadorEstado montador;
    private final Clock relogio;

    public ServicoConversa(
            SessaoCadastroRepository sessoes,
            RascunhoCadastroRepository rascunhos,
            MensagemProcessadaRepository mensagens,
            RoteiroCadastro roteiro,
            MontadorEstado montador,
            Clock relogio) {
        this.sessoes = sessoes;
        this.rascunhos = rascunhos;
        this.mensagens = mensagens;
        this.roteiro = roteiro;
        this.montador = montador;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public EstadoConversa estadoAtual(UUID sessaoId) {
        return montador.montar(sessao(sessaoId), rascunho(sessaoId), List.of());
    }

    @Transactional
    public EstadoConversa responder(UUID sessaoId, UUID mensagemId, int versao, RespostaEntrada entrada) {
        var sessao = sessao(sessaoId);
        var rascunho = rascunho(sessaoId);
        // Reenvio da mesma mensagem: devolve o estado atual sem aplicar de novo.
        if (mensagens.existsById(new MensagemProcessada.Chave(sessaoId, mensagemId))) {
            return montador.montar(sessao, rascunho, List.of());
        }
        if (versao != rascunho.getVersao()) {
            throw new VersaoDesatualizadaException();
        }
        if (sessao.getEstado() != EstadoSessao.EM_PREENCHIMENTO) {
            throw new EstadoInvalidoException("Não há pergunta aguardando resposta.");
        }
        var pergunta = roteiro.proxima(rascunho)
                .orElseThrow(() -> new EstadoInvalidoException("Não há pergunta aguardando resposta."));
        var agora = relogio.instant();

        switch (pergunta.resolver(entrada, contexto(rascunho, agora))) {
            case Resolucao.Valor aceito -> rascunho.informar(pergunta.campo(), aceito.valor(), agora);
            case Resolucao.Ausente ausente -> rascunho.registrarAusencia(pergunta.campo(), ausente.motivo(), agora);
            case Resolucao.Invalida invalida ->
                    throw new RespostaInvalidaException(pergunta.campo(), invalida.mensagem());
        }

        sessao.mudarPara(roteiro.proxima(rascunho).isPresent()
                ? EstadoSessao.EM_PREENCHIMENTO
                : EstadoSessao.EM_REVISAO);
        rascunhos.flush();
        mensagens.save(new MensagemProcessada(sessaoId, mensagemId, versao, rascunho.getVersao(), agora));
        return montador.montar(sessao, rascunho, List.of());
    }

    private SessaoCadastro sessao(UUID sessaoId) {
        return sessoes.findById(sessaoId).orElseThrow(SessaoInexistenteException::new);
    }

    private RascunhoCadastro rascunho(UUID sessaoId) {
        return rascunhos.findById(sessaoId)
                .orElseThrow(() -> new EstadoInvalidoException("Este cadastro não está mais em preenchimento."));
    }

    private ContextoResposta contexto(RascunhoCadastro rascunho, Instant agora) {
        return new ContextoResposta(rascunho, LocalDate.ofInstant(agora, FUSO));
    }
}
