package io.github.deborahrod.defensoria.cadastro;

import java.util.List;

public enum EstadoSessao {
    EM_PREENCHIMENTO,
    AGUARDANDO_ESCLARECIMENTO,
    EM_REVISAO,
    CONFIRMADO,
    CANCELADO,
    EXPIRADO;

    public static final List<EstadoSessao> EM_ANDAMENTO =
            List.of(EM_PREENCHIMENTO, AGUARDANDO_ESCLARECIMENTO, EM_REVISAO);

    public boolean emAndamento() {
        return EM_ANDAMENTO.contains(this);
    }
}
