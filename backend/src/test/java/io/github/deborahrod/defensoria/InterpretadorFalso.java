package io.github.deborahrod.defensoria;

import io.github.deborahrod.defensoria.integracao.FalhaInterpretacaoException;
import io.github.deborahrod.defensoria.integracao.FalhaInterpretacaoException.Categoria;
import io.github.deborahrod.defensoria.integracao.InterpretadorRespostas;
import io.github.deborahrod.defensoria.integracao.PedidoInterpretacao;
import io.github.deborahrod.defensoria.integracao.ResultadoInterpretacao;
import io.github.deborahrod.defensoria.integracao.ResultadoInterpretacao.Candidato;
import io.github.deborahrod.defensoria.integracao.ResultadoInterpretacao.Operacao;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

/** Interpretador programável dos testes. Chamada não programada é erro, para flagrar uso indevido. */
public class InterpretadorFalso implements InterpretadorRespostas {

    public final List<PedidoInterpretacao> pedidos = new CopyOnWriteArrayList<>();
    private Function<PedidoInterpretacao, ResultadoInterpretacao> comportamento = naoProgramado();

    public void reiniciar() {
        pedidos.clear();
        comportamento = naoProgramado();
    }

    public void propor(String... camposValoresETrechos) {
        responderCom(Operacao.PROPOR_VALORES, false, camposValoresETrechos);
    }

    public void responderCom(Operacao operacao, boolean precisaEsclarecimento, String... camposValoresETrechos) {
        var candidatos = new ArrayList<Candidato>();
        for (var i = 0; i < camposValoresETrechos.length; i += 3) {
            candidatos.add(new Candidato(camposValoresETrechos[i], camposValoresETrechos[i + 1], camposValoresETrechos[i + 2]));
        }
        var resultado = new ResultadoInterpretacao(operacao, candidatos, precisaEsclarecimento);
        comportamento = pedido -> resultado;
    }

    /** O modelo aponta um tópico do catálogo de ajuda. */
    public void ajuda(String topico) {
        var resultado = new ResultadoInterpretacao(Operacao.AJUDA, List.of(), false, topico);
        comportamento = pedido -> resultado;
    }

    /** A chamada fica parada até o teste liberar, para simular um provedor lento. */
    public void bloquearAte(CountDownLatch entrou, CountDownLatch liberar, ResultadoInterpretacao resultado) {
        comportamento = pedido -> {
            entrou.countDown();
            try {
                liberar.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return resultado;
        };
    }

    public void falharCom(Categoria categoria) {
        comportamento = pedido -> {
            throw new FalhaInterpretacaoException(categoria);
        };
    }

    @Override
    public ResultadoInterpretacao interpretar(PedidoInterpretacao pedido) {
        pedidos.add(pedido);
        return comportamento.apply(pedido);
    }

    private static Function<PedidoInterpretacao, ResultadoInterpretacao> naoProgramado() {
        return pedido -> {
            throw new IllegalStateException("O interpretador não deveria ter sido chamado neste teste.");
        };
    }
}
