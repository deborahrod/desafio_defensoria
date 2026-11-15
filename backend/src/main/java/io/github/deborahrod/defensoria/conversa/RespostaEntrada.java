package io.github.deborahrod.defensoria.conversa;

/** Resposta de um turno: um valor digitado ou a opção escolhida. */
public record RespostaEntrada(String valor, String opcao) {

    public static RespostaEntrada valor(String valor) {
        return new RespostaEntrada(valor, null);
    }

    public static RespostaEntrada opcao(String opcao) {
        return new RespostaEntrada(null, opcao);
    }
}
