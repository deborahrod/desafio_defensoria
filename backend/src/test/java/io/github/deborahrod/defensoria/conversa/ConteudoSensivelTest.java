package io.github.deborahrod.defensoria.conversa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConteudoSensivelTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Sofro violência em casa",
            "VIOLÊNCIA doméstica",
            "fui presa ontem",
            "meu filho está preso",
            "tenho depressão e tomo remédio",
            "recebi um diagnóstico difícil",
            "minha religião é evangélica",
            "tenho medo de ameaças do ex"})
    void barraRelatoComDetalhesDoCaso(String texto) {
        assertThat(ConteudoSensivel.em(texto)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Moro em Anápolis, alugado, com minha mãe e dois filhos. Faço bico de pedreiro.",
            "Sou diarista e moro de favor na casa da minha irmã",
            "Estou desempregada e ganho uns mil reais de bicos",
            "moro em Goiânia, no setor Marista"})
    void deixaPassarRelatoComDadosBasicos(String texto) {
        assertThat(ConteudoSensivel.em(texto)).isFalse();
    }
}
