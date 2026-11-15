package io.github.deborahrod.defensoria.conversa;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Apresentação dos valores guardados no rascunho. */
final class Formatador {

    private static final Locale BRASIL = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Formatador() {
    }

    static String exibir(TipoEntrada tipo, String valor) {
        return switch (tipo) {
            case DATA -> LocalDate.parse(valor).format(DATA);
            case CPF -> valor.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
            case CEP -> valor.replaceFirst("(\\d{5})(\\d{3})", "$1-$2");
            case TELEFONE -> telefone(valor);
            case VALOR -> reais(valor);
            default -> valor;
        };
    }

    static String telefone(String valor) {
        if (valor.startsWith("+")) {
            return valor;
        }
        return valor.length() == 11
                ? valor.replaceFirst("(\\d{2})(\\d{5})(\\d{4})", "($1) $2-$3")
                : valor.replaceFirst("(\\d{2})(\\d{4})(\\d{4})", "($1) $2-$3");
    }

    static String reais(String valor) {
        var formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(BRASIL));
        return "R$ " + formato.format(new BigDecimal(valor));
    }
}
