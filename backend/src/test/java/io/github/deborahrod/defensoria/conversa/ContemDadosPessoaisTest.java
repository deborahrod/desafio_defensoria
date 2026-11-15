package io.github.deborahrod.defensoria.conversa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContemDadosPessoaisTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "meu CPF é 529.982.247-25 e moro em Goiânia",
            "52998224725",
            "me liga no (62) 91234-5678",
            "62 3201 0000",
            "escreve para ana@exemplo.com",
            "ana@"})
    void identificaCpfTelefoneEEmail(String texto) {
        assertThat(ContemDadosPessoais.em(texto)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Moro em Goiânia e estou desempregada",
            "Trabalho como diarista há 12 anos",
            "Setor Marista, quadra 217 lote 14",
            "sou autônomo"})
    void textoComumPassa(String texto) {
        assertThat(ContemDadosPessoais.em(texto)).isFalse();
    }
}
