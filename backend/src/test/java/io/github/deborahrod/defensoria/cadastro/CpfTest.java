package io.github.deborahrod.defensoria.cadastro;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CpfTest {

    @ParameterizedTest
    @ValueSource(strings = {"52998224725", "11144477735", "39053344705"})
    void aceitaDigitosVerificadoresCorretos(String cpf) {
        assertThat(Cpf.valido(cpf)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "52998224724", "52998224735", "00000000000", "11111111111", "5299822472", "529982247250",
            "529.982.247-25", "abcdefghijk", ""})
    void recusaDigitoErradoSequenciaRepetidaETamanhoIncorreto(String cpf) {
        assertThat(Cpf.valido(cpf)).isFalse();
    }
}
