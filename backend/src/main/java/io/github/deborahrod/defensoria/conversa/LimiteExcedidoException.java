package io.github.deborahrod.defensoria.conversa;

/** Limite de uso da própria aplicação. Quando há intervalo de espera, ele é informado ao cliente. */
public class LimiteExcedidoException extends RuntimeException {

    private final String motivo;
    private final Long esperaEmSegundos;

    public LimiteExcedidoException(String motivo, String mensagem, Long esperaEmSegundos) {
        super(mensagem);
        this.motivo = motivo;
        this.esperaEmSegundos = esperaEmSegundos;
    }

    public String getMotivo() {
        return motivo;
    }

    public Long getEsperaEmSegundos() {
        return esperaEmSegundos;
    }
}
