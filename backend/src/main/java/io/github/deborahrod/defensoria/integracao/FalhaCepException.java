package io.github.deborahrod.defensoria.integracao;

/** O provedor não respondeu a tempo ou respondeu algo fora do formato. A mensagem nunca traz o CEP. */
class FalhaCepException extends Exception {

    FalhaCepException(String motivo) {
        super(motivo);
    }

    FalhaCepException(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
