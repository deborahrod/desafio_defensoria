package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.integracao.ServicoEventos;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Limites em memória, válidos para uma única instância da aplicação. Em mais de uma instância
 * o estado precisaria ficar num armazenamento compartilhado.
 */
@Component
public class LimitadorRequisicoes {

    private static final Duration JANELA = Duration.ofMinutes(1);
    private static final Duration INATIVIDADE_MAXIMA = Duration.ofHours(1);

    /** Origens acompanhadas ao mesmo tempo, mais um balde para as excedentes. Passando disso, as novas o dividem. */
    static final int MAXIMO_DE_ORIGENS = 10_000;
    private static final String ORIGENS_EXCEDENTES = "(excedentes)";

    /** Marca uma operação em andamento. Fechar libera a sessão para a próxima. */
    public interface Operacao extends AutoCloseable {
        @Override
        void close();
    }

    private static final class Janela {
        final Deque<Instant> instantes = new ArrayDeque<>();
        int total;
        Instant ultimoUso;
    }

    private final PropriedadesLimites limites;
    private final Clock relogio;
    private final ServicoEventos eventos;
    private final Map<UUID, Janela> porSessao = new ConcurrentHashMap<>();
    private final Map<String, Janela> porOrigem = new ConcurrentHashMap<>();
    private final Set<UUID> emAndamento = ConcurrentHashMap.newKeySet();
    private final int maximoDeOrigens;

    @Autowired
    public LimitadorRequisicoes(PropriedadesLimites limites, Clock relogio, ServicoEventos eventos) {
        this(limites, relogio, eventos, MAXIMO_DE_ORIGENS);
    }

    LimitadorRequisicoes(PropriedadesLimites limites, Clock relogio, ServicoEventos eventos, int maximoDeOrigens) {
        this.limites = limites;
        this.relogio = relogio;
        this.eventos = eventos;
        this.maximoDeOrigens = maximoDeOrigens;
    }

    /**
     * Reserva a sessão para uma operação e conta a resposta. Uma sessão só tem uma operação por vez,
     * o que também garante no máximo uma inferência em andamento por sessão.
     */
    public Operacao iniciarResposta(UUID sessaoId) {
        if (!emAndamento.add(sessaoId)) {
            throw recusar("CONCORRENCIA", "Aguarde a resposta anterior terminar e tente de novo.", 2L);
        }
        try {
            contarResposta(sessaoId);
        } catch (RuntimeException e) {
            emAndamento.remove(sessaoId);
            throw e;
        }
        return () -> emAndamento.remove(sessaoId);
    }

    /** Devolve quantos segundos esperar, ou zero se a origem pode seguir. */
    public long permitirOrigem(String origem) {
        var agora = relogio.instant();
        var janela = janelaDaOrigem(origem, agora);
        synchronized (janela) {
            janela.ultimoUso = agora;
            podar(janela, agora);
            if (janela.instantes.size() >= limites.requisicoesPorOrigemPorMinuto()) {
                return esperaAte(janela, agora);
            }
            janela.instantes.addLast(agora);
            return 0;
        }
    }

    /**
     * O mapa não cresce sem limite, mesmo que a chave venha de quem faz as requisições. Cheio, primeiro
     * descarta as origens que não pedem há mais de uma janela, que já não têm nada a contar. Se ainda
     * estiver cheio, as origens novas dividem um balde só, sujeito ao mesmo teto.
     */
    private Janela janelaDaOrigem(String origem, Instant agora) {
        var existente = porOrigem.get(origem);
        if (existente != null) {
            return existente;
        }
        if (porOrigem.size() >= maximoDeOrigens) {
            var ocioso = agora.minus(JANELA);
            porOrigem.values().removeIf(janela -> janela.ultimoUso.isBefore(ocioso));
        }
        var chave = porOrigem.size() >= maximoDeOrigens ? ORIGENS_EXCEDENTES : origem;
        return porOrigem.computeIfAbsent(chave, k -> new Janela());
    }

    int origensAcompanhadas() {
        return porOrigem.size();
    }

    public void descartarInativos() {
        var limite = relogio.instant().minus(INATIVIDADE_MAXIMA);
        porSessao.values().removeIf(janela -> janela.ultimoUso.isBefore(limite));
        porOrigem.values().removeIf(janela -> janela.ultimoUso.isBefore(limite));
    }

    private void contarResposta(UUID sessaoId) {
        var agora = relogio.instant();
        var janela = porSessao.computeIfAbsent(sessaoId, id -> new Janela());
        synchronized (janela) {
            janela.ultimoUso = agora;
            podar(janela, agora);
            if (janela.total >= limites.respostasPorSessao()) {
                throw recusar("RESPOSTAS_POR_SESSAO",
                        "Você atingiu o limite de respostas desta sessão. Encerre e comece um novo cadastro.", null);
            }
            if (janela.instantes.size() >= limites.respostasPorMinuto()) {
                var espera = esperaAte(janela, agora);
                throw recusar("RESPOSTAS_POR_MINUTO",
                        "Muitas respostas em pouco tempo. Aguarde " + espera + " segundos e tente de novo.", espera);
            }
            janela.instantes.addLast(agora);
            janela.total++;
        }
    }

    private LimiteExcedidoException recusar(String motivo, String mensagem, Long espera) {
        eventos.registrar("LIMITE", motivo, null, null, null);
        return new LimiteExcedidoException(motivo, mensagem, espera);
    }

    private static void podar(Janela janela, Instant agora) {
        var corte = agora.minus(JANELA);
        while (!janela.instantes.isEmpty() && !janela.instantes.peekFirst().isAfter(corte)) {
            janela.instantes.removeFirst();
        }
    }

    private static long esperaAte(Janela janela, Instant agora) {
        var libera = janela.instantes.peekFirst().plus(JANELA);
        return Math.max(1, Duration.between(agora, libera).toSeconds() + 1);
    }
}
