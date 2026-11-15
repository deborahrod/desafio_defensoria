package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.cadastro.Campo;

public class RespostaInvalidaException extends RuntimeException {

    private final Campo campo;

    public RespostaInvalidaException(Campo campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public Campo getCampo() {
        return campo;
    }
}
