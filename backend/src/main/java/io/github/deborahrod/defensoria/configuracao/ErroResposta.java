package io.github.deborahrod.defensoria.configuracao;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErroResposta(String codigo, String mensagem, String campo) {

    public ErroResposta(String codigo, String mensagem) {
        this(codigo, mensagem, null);
    }
}
