package io.github.deborahrod.defensoria.conversa;

/** A operação pedida não cabe no estado atual da sessão. */
public class EstadoInvalidoException extends RuntimeException {

    public EstadoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
