package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.cadastro.Campo;

public class RespostaInvalidaException extends RuntimeException {

    private final String campo;

    public RespostaInvalidaException(Campo campo, String mensagem) {
        this(campo.id(), mensagem);
    }

    public RespostaInvalidaException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
