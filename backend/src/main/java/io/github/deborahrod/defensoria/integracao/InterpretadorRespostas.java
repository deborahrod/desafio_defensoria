package io.github.deborahrod.defensoria.integracao;

/** Interpreta texto livre. Propõe valores; quem decide o que entra no cadastro é o servidor. */
public interface InterpretadorRespostas {

    ResultadoInterpretacao interpretar(PedidoInterpretacao pedido);
}
