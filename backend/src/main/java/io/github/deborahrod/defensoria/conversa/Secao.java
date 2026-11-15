package io.github.deborahrod.defensoria.conversa;

public enum Secao {
    QUEM_PREENCHE("Quem preenche"),
    IDENTIFICACAO("Identificação"),
    CONTATO("Contato"),
    LOCALIZACAO("Localização"),
    SOCIOECONOMICO("Situação socioeconômica");

    private final String titulo;

    Secao(String titulo) {
        this.titulo = titulo;
    }

    public String titulo() {
        return titulo;
    }
}
