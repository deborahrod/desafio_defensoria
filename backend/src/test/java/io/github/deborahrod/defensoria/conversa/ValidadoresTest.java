package io.github.deborahrod.defensoria.conversa;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.deborahrod.defensoria.cadastro.Campo;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastro;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ValidadoresTest {

    private static final Instant AGORA = Instant.parse("2026-11-15T12:00:00Z");

    private final RascunhoCadastro rascunho = new RascunhoCadastro(UUID.randomUUID(), AGORA);
    private final ContextoResposta contexto = new ContextoResposta(rascunho, LocalDate.of(2026, 11, 15));

    @ParameterizedTest
    @ValueSource(strings = {"Madonna", "José d'Ávila Conceição", "Nguyễn Thị Minh", "Zoë Müller-Østergård", "李小龍"})
    void nomeAceitaNomeUnicoAcentosEGrafiasEstrangeiras(String nome) {
        assertThat(Validadores.nome(nome, contexto)).isEqualTo(Validacao.aceita(nome));
    }

    @Test
    void nomeRemoveEspacosSobrando() {
        assertThat(Validadores.nome("  Ana   Fictícia ", contexto)).isEqualTo(Validacao.aceita("Ana Fictícia"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "12345", "---"})
    void nomeRecusaVazioOuSemLetras(String nome) {
        assertThat(Validadores.nome(nome, contexto)).isInstanceOf(Validacao.Recusada.class);
    }

    @Test
    void nomeRecusaMaisDeDuzentosCaracteres() {
        assertThat(Validadores.nome("a".repeat(200), contexto)).isInstanceOf(Validacao.Aceita.class);
        assertThat(Validadores.nome("a".repeat(201), contexto)).isInstanceOf(Validacao.Recusada.class);
    }

    @ParameterizedTest
    @CsvSource({"20/05/1990, 1990-05-20", "1990-05-20, 1990-05-20", "15/11/2026, 2026-11-15"})
    void nascimentoAceitaDataValidaAteHoje(String entrada, String esperado) {
        assertThat(Validadores.nascimento(entrada, contexto)).isEqualTo(Validacao.aceita(esperado));
    }

    @ParameterizedTest
    @ValueSource(strings = {"16/11/2026", "31/02/1990", "20/05/1850", "ontem", "1990"})
    void nascimentoRecusaFuturoDataInexistenteEFormatoLivre(String entrada) {
        assertThat(Validadores.nascimento(entrada, contexto)).isInstanceOf(Validacao.Recusada.class);
    }

    @Test
    void cpfAceitaComOuSemMascaraERecusaDigitoErrado() {
        assertThat(Validadores.cpf("529.982.247-25", contexto)).isEqualTo(Validacao.aceita("52998224725"));
        assertThat(Validadores.cpf("52998224725", contexto)).isEqualTo(Validacao.aceita("52998224725"));
        assertThat(Validadores.cpf("529.982.247-26", contexto)).isInstanceOf(Validacao.Recusada.class);
        assertThat(Validadores.cpf("000.000.000-00", contexto)).isInstanceOf(Validacao.Recusada.class);
    }

    @ParameterizedTest
    @CsvSource({
            "(62) 91234-5678, 62912345678",
            "62 3201-0000, 6232010000",
            "+55 62 91234-5678, 62912345678",
            "+351 912 345 678, +351912345678"})
    void telefoneAceitaNumeroNacionalComDddEInternacional(String entrada, String esperado) {
        assertThat(Validadores.telefone(entrada, contexto)).isEqualTo(Validacao.aceita(esperado));
    }

    @ParameterizedTest
    @ValueSource(strings = {"91234-5678", "(02) 91234-5678", "123", "telefone", "+12"})
    void telefoneRecusaSemDddOuIncompleto(String entrada) {
        assertThat(Validadores.telefone(entrada, contexto)).isInstanceOf(Validacao.Recusada.class);
    }

    @Test
    void emailConfereApenasAEstrutura() {
        assertThat(Validadores.email(" ana@exemplo.com ", contexto)).isEqualTo(Validacao.aceita("ana@exemplo.com"));
        assertThat(Validadores.email("ana@exemplo", contexto)).isInstanceOf(Validacao.Recusada.class);
        assertThat(Validadores.email("ana exemplo.com", contexto)).isInstanceOf(Validacao.Recusada.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "1.518,35; 1518.35",
            "R$ 1.500,00; 1500.00",
            "1500; 1500.00",
            "1500,5; 1500.50",
            "1500.50; 1500.50",
            "1.500; 1500.00",
            "0; 0.00"})
    void valorEmReaisEntendeFormatosBrasileiros(String entrada, String esperado) {
        assertThat(Validadores.valorEmReais(entrada, contexto)).isEqualTo(Validacao.aceita(esperado));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-100", "mil reais", "1.500,000", "", "1,5,0"})
    void valorEmReaisRecusaNegativoETexto(String entrada) {
        assertThat(Validadores.valorEmReais(entrada, contexto)).isInstanceOf(Validacao.Recusada.class);
    }

    @Test
    void nucleoFamiliarTemPeloMenosUmaPessoa() {
        assertThat(Validadores.pessoasNoNucleo("1", contexto)).isEqualTo(Validacao.aceita("1"));
        assertThat(Validadores.pessoasNoNucleo("0", contexto)).isInstanceOf(Validacao.Recusada.class);
        assertThat(Validadores.pessoasNoNucleo("três", contexto)).isInstanceOf(Validacao.Recusada.class);
    }

    @Test
    void dependentesPrecisamCaberNoNucleoInformado() {
        rascunho.informar(Campo.QUANTIDADE_MEMBROS, "3", AGORA);

        assertThat(Validadores.dependentes("2", contexto)).isEqualTo(Validacao.aceita("2"));
        assertThat(Validadores.dependentes("0", contexto)).isEqualTo(Validacao.aceita("0"));
        assertThat(Validadores.dependentes("3", contexto)).isInstanceOf(Validacao.Recusada.class);
    }

    @Test
    void valoresNormalizadosPassamDeNovoPelaMesmaRegra() {
        assertThat(Validadores.telefone("+351912345678", contexto)).isEqualTo(Validacao.aceita("+351912345678"));
        assertThat(Validadores.valorEmReais("1518.35", contexto)).isEqualTo(Validacao.aceita("1518.35"));
        assertThat(Validadores.cep("74000000", contexto)).isEqualTo(Validacao.aceita("74000000"));
    }
}
