package io.github.deborahrod.defensoria.cadastro;

import java.util.List;
import java.util.Map;

/**
 * Mudança que só entra no rascunho depois de a pessoa confirmar: valores e ausências a
 * registrar e campos que seriam apagados em consequência.
 */
public record Esclarecimento(
        Tipo tipo, Map<String, String> valores, Map<String, String> ausencias, List<String> afetados) {

    public enum Tipo {
        CONFIRMAR_LIMPEZA,
        CONFIRMAR_INTERPRETACAO
    }
}
