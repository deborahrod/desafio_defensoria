package io.github.deborahrod.defensoria.conversa;

import java.util.regex.Pattern;

/**
 * Evita enviar ao serviço de IA identificadores escritos fora do campo próprio. É um filtro simples:
 * reduz a exposição, mas não garante anonimização.
 */
final class ContemDadosPessoais {

    private static final Pattern SEQUENCIA_NUMERICA = Pattern.compile("(?:\\d[\\s.\\-()]?){8,}");

    private ContemDadosPessoais() {
    }

    static boolean em(String texto) {
        return texto.contains("@") || SEQUENCIA_NUMERICA.matcher(texto).find();
    }
}
