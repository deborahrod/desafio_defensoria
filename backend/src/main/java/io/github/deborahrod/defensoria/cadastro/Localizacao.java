package io.github.deborahrod.defensoria.cadastro;

import jakarta.persistence.Embeddable;

@Embeddable
public record Localizacao(
        String municipio,
        String uf,
        Boolean enderecoFixo,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cep,
        String referenciaLocalizacao) {

    public static Localizacao naoInformada() {
        return new Localizacao(null, null, null, null, null, null, null, null, null);
    }
}
