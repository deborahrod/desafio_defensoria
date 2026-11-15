package io.github.deborahrod.defensoria.cadastro;

import java.util.List;
import java.util.Set;

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

    public boolean podeIrPara(EstadoSessao destino) {
        return destinos().contains(destino);
    }

    private Set<EstadoSessao> destinos() {
        return switch (this) {
            case EM_PREENCHIMENTO -> Set.of(AGUARDANDO_ESCLARECIMENTO, EM_REVISAO, CANCELADO, EXPIRADO);
            case AGUARDANDO_ESCLARECIMENTO -> Set.of(EM_PREENCHIMENTO, EM_REVISAO, CANCELADO, EXPIRADO);
            // A confirmação só é alcançada a partir da revisão.
            case EM_REVISAO -> Set.of(EM_PREENCHIMENTO, AGUARDANDO_ESCLARECIMENTO, CONFIRMADO, CANCELADO, EXPIRADO);
            case CONFIRMADO, CANCELADO, EXPIRADO -> Set.of();
        };
    }
}
