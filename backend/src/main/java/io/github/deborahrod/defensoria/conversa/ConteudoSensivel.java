package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.integracao.NormalizacaoDeTexto;
import java.util.regex.Pattern;

/**
 * Barra, antes de qualquer chamada externa, relato que entre em detalhes do caso (violência, saúde,
 * religião, processos). O cadastro é básico: esse tipo de conteúdo não deve ir ao modelo.
 * O filtro é por radicais e não substitui o aviso ao usuário.
 */
final class ConteudoSensivel {

    private static final Pattern TERMOS = Pattern.compile(
            "(?<![a-z])(violenc|agress|abus|estupr|ameac|espanc|maria da penha|preso|presa|prisao|cadeia|crime|criminal"
                    + "|diagnostic|doenca|cancer|hiv|aids|depress|suicid|psiquiatr|transtorno|religi|igreja|candomble"
                    + "|umbanda|evangelic|catolic|homossexual|lesbica)");

    private ConteudoSensivel() {
    }

    static boolean em(String texto) {
        return TERMOS.matcher(NormalizacaoDeTexto.normalizar(texto)).find();
    }
}
