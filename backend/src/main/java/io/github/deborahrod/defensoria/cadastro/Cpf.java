package io.github.deborahrod.defensoria.cadastro;

/** Confere apenas os dígitos verificadores. Não comprova identidade nem situação cadastral. */
public final class Cpf {

    private Cpf() {
    }

    public static boolean valido(String digitos) {
        if (digitos == null || !digitos.matches("\\d{11}") || digitos.chars().distinct().count() == 1) {
            return false;
        }
        return digito(digitos, 9) == digitos.charAt(9) - '0'
                && digito(digitos, 10) == digitos.charAt(10) - '0';
    }

    private static int digito(String digitos, int quantidade) {
        var soma = 0;
        for (var i = 0; i < quantidade; i++) {
            soma += (digitos.charAt(i) - '0') * (quantidade + 1 - i);
        }
        var resto = soma * 10 % 11;
        return resto == 10 ? 0 : resto;
    }
}
