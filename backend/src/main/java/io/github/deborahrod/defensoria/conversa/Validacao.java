package io.github.deborahrod.defensoria.conversa;

public sealed interface Validacao {

    record Aceita(String valor) implements Validacao {
    }

    record Recusada(String mensagem) implements Validacao {
    }

    static Validacao aceita(String valor) {
        return new Aceita(valor);
    }

    static Validacao recusada(String mensagem) {
        return new Recusada(mensagem);
    }
}
