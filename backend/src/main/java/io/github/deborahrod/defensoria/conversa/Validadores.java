package io.github.deborahrod.defensoria.conversa;

import static io.github.deborahrod.defensoria.conversa.Validacao.aceita;
import static io.github.deborahrod.defensoria.conversa.Validacao.recusada;

import io.github.deborahrod.defensoria.cadastro.Campo;
import io.github.deborahrod.defensoria.cadastro.Cpf;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

/** Regras de cada campo. Todo valor aceito sai normalizado e passa de novo pela mesma regra. */
final class Validadores {

    private static final DateTimeFormatter DATA_BR =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final LocalDate DATA_MINIMA = LocalDate.of(1900, 1, 1);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final Pattern VALOR_COM_MILHAR = Pattern.compile("\\d{1,3}(\\.\\d{3})+(,\\d{1,2})?");
    private static final Pattern VALOR_COM_VIRGULA = Pattern.compile("\\d+(,\\d{1,2})?");
    private static final Pattern VALOR_COM_PONTO = Pattern.compile("\\d+\\.\\d{1,2}");
    private static final BigDecimal VALOR_MAXIMO = new BigDecimal("9999999999999.99");

    private Validadores() {
    }

    static Validacao nome(String bruto, ContextoResposta contexto) {
        var limpo = compactar(bruto);
        if (limpo.isEmpty()) {
            return recusada("Preciso que você informe o nome para continuar.");
        }
        if (limpo.length() > 200) {
            return recusada("O nome pode ter até 200 caracteres.");
        }
        if (limpo.codePoints().noneMatch(Character::isLetter) || temCaractereDeControle(limpo)) {
            return recusada("Informe o nome usando letras.");
        }
        return aceita(limpo);
    }

    static Pergunta.Validador texto(int tamanhoMaximo) {
        return (bruto, contexto) -> {
            var limpo = compactar(bruto);
            if (limpo.isEmpty()) {
                return recusada("Escreva uma resposta ou escolha uma das opções.");
            }
            if (limpo.length() > tamanhoMaximo) {
                return recusada("A resposta pode ter até " + tamanhoMaximo + " caracteres.");
            }
            if (temCaractereDeControle(limpo)) {
                return recusada("A resposta contém caracteres que não podem ser usados.");
            }
            return aceita(limpo);
        };
    }

    static Validacao nascimento(String bruto, ContextoResposta contexto) {
        LocalDate data;
        try {
            var limpo = bruto.strip();
            data = limpo.contains("/") ? LocalDate.parse(limpo, DATA_BR) : LocalDate.parse(limpo);
        } catch (DateTimeParseException e) {
            return recusada("Informe a data com dia, mês e ano, por exemplo 20/05/1990.");
        }
        if (data.isAfter(contexto.hoje())) {
            return recusada("A data de nascimento não pode estar no futuro.");
        }
        if (data.isBefore(DATA_MINIMA)) {
            return recusada("Confira o ano informado.");
        }
        return aceita(data.toString());
    }

    static Validacao cpf(String bruto, ContextoResposta contexto) {
        var digitos = bruto.replaceAll("\\D", "");
        return Cpf.valido(digitos)
                ? aceita(digitos)
                : recusada("Esse CPF não é válido. Confira os 11 números.");
    }

    static Validacao telefone(String bruto, ContextoResposta contexto) {
        var limpo = bruto.strip();
        var digitos = limpo.replaceAll("\\D", "");
        if (limpo.startsWith("+") && !digitos.startsWith("55")) {
            return digitos.length() >= 8 && digitos.length() <= 15
                    ? aceita("+" + digitos)
                    : recusada("Informe o número com código do país e da região.");
        }
        if (limpo.startsWith("+")) {
            digitos = digitos.substring(2);
        }
        var comDdd = digitos.length() == 10 || digitos.length() == 11;
        if (!comDdd || digitos.charAt(0) == '0' || digitos.charAt(1) == '0') {
            return recusada("Informe o telefone com DDD, por exemplo (62) 91234-5678.");
        }
        return aceita(digitos);
    }

    static Validacao email(String bruto, ContextoResposta contexto) {
        var limpo = bruto.strip();
        return limpo.length() <= 254 && EMAIL.matcher(limpo).matches()
                ? aceita(limpo)
                : recusada("Confira o e-mail. Ele precisa ter o formato nome@exemplo.com.");
    }

    static Validacao telefoneOuEmail(String bruto, ContextoResposta contexto) {
        var comoEmail = email(bruto, contexto);
        if (comoEmail instanceof Validacao.Aceita) {
            return comoEmail;
        }
        return telefone(bruto, contexto) instanceof Validacao.Aceita aceito
                ? aceito
                : recusada("Informe um telefone com DDD ou um e-mail dessa pessoa.");
    }

    static Validacao cep(String bruto, ContextoResposta contexto) {
        var digitos = bruto.replaceAll("\\D", "");
        return digitos.length() == 8 ? aceita(digitos) : recusada("O CEP tem 8 números.");
    }

    static Validacao valorEmReais(String bruto, ContextoResposta contexto) {
        var limpo = bruto.replace("R$", "").replaceAll("\\s", "");
        String numero;
        if (VALOR_COM_MILHAR.matcher(limpo).matches()) {
            numero = limpo.replace(".", "").replace(',', '.');
        } else if (VALOR_COM_VIRGULA.matcher(limpo).matches()) {
            numero = limpo.replace(',', '.');
        } else if (VALOR_COM_PONTO.matcher(limpo).matches()) {
            numero = limpo;
        } else {
            return recusada("Informe o valor em reais usando números, por exemplo 1.500,00.");
        }
        var valor = new BigDecimal(numero).setScale(2, RoundingMode.UNNECESSARY);
        return valor.compareTo(VALOR_MAXIMO) <= 0
                ? aceita(valor.toPlainString())
                : recusada("Confira o valor informado.");
    }

    static Validacao pessoasNoNucleo(String bruto, ContextoResposta contexto) {
        var quantidade = inteiro(bruto);
        return quantidade != null && quantidade >= 1 && quantidade <= 50
                ? aceita(quantidade.toString())
                : recusada("Informe um número a partir de 1. Conte com você.");
    }

    static Validacao dependentes(String bruto, ContextoResposta contexto) {
        var quantidade = inteiro(bruto);
        if (quantidade == null || quantidade < 0 || quantidade > 49) {
            return recusada("Informe um número, usando 0 se ninguém depender de você.");
        }
        var pessoas = contexto.rascunho().valor(Campo.QUANTIDADE_MEMBROS).map(Integer::valueOf);
        if (pessoas.isPresent() && quantidade >= pessoas.get()) {
            return recusada("O número de dependentes precisa ser menor que o total de pessoas informado ("
                    + pessoas.get() + ").");
        }
        return aceita(quantidade.toString());
    }

    private static Integer inteiro(String bruto) {
        var limpo = bruto.strip();
        return limpo.matches("\\d{1,3}") ? Integer.valueOf(limpo) : null;
    }

    private static String compactar(String bruto) {
        return bruto.strip().replaceAll("\\s+", " ");
    }

    private static boolean temCaractereDeControle(String texto) {
        return texto.codePoints().anyMatch(Character::isISOControl);
    }
}
