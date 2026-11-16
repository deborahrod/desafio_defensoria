package io.github.deborahrod.defensoria.integracao;

import java.text.Normalizer;
import java.util.Locale;

/** Compara textos sem depender de acento, caixa ou espaços repetidos. */
public final class NormalizacaoDeTexto {

    private NormalizacaoDeTexto() {
    }

    public static String normalizar(String texto) {
        var semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
