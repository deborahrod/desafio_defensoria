package io.github.deborahrod.defensoria.integracao;

import io.github.deborahrod.defensoria.cadastro.ModoInteracao;
import org.springframework.stereotype.Service;

/** Chama o interpretador fora de qualquer transação e transforma falhas em categorias. */
@Service
public class ServicoInterpretacao {

    private final InterpretadorRespostas interpretador;
    private final PropriedadesLlm llm;
    private final ServicoEventos eventos;

    public ServicoInterpretacao(InterpretadorRespostas interpretador, PropriedadesLlm llm, ServicoEventos eventos) {
        this.interpretador = interpretador;
        this.llm = llm;
        this.eventos = eventos;
    }

    public Interpretacao interpretar(PedidoInterpretacao pedido) {
        var inicio = System.nanoTime();
        try {
            var resultado = interpretador.interpretar(pedido);
            registrar("SUCESSO", inicio);
            return new Interpretacao.Sucesso(resultado);
        } catch (FalhaInterpretacaoException falha) {
            registrar(falha.categoria().name(), inicio);
            return new Interpretacao.Falha(falha.categoria());
        }
    }

    private void registrar(String status, long inicio) {
        var duracao = (System.nanoTime() - inicio) / 1_000_000;
        eventos.registrar("INFERENCIA", status, ModoInteracao.ASSISTIDO_POR_IA, llm.model(), duracao);
    }
}
