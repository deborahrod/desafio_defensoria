package io.github.deborahrod.defensoria.conversa;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.deborahrod.defensoria.cadastro.Campo;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastro;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

    private static final List<String> DDDS_VALIDOS = List.of(
            "11", "12", "13", "14", "15", "16", "17", "18", "19", "21", "22", "24", "27", "28",
            "31", "32", "33", "34", "35", "37", "38", "41", "42", "43", "44", "45", "46", "47", "48", "49",
            "51", "53", "54", "55", "61", "62", "63", "64", "65", "66", "67", "68", "69",
            "71", "73", "74", "75", "77", "79", "81", "82", "83", "84", "85", "86", "87", "88", "89",
            "91", "92", "93", "94", "95", "96", "97", "98", "99");

    private static String mensagem(Validacao validacao) {
        return ((Validacao.Recusada) validacao).mensagem();
    }

    @Test
    void todosOsDddsBrasileirosSaoAceitosEOsDemaisRecusados() {
        assertThat(DDDS_VALIDOS).hasSize(67);
        for (var ddd = 10; ddd <= 99; ddd++) {
            var codigo = String.valueOf(ddd);
            var celular = Validadores.telefone(codigo + "912345678", contexto);
            if (DDDS_VALIDOS.contains(codigo)) {
                assertThat(celular).as("DDD " + codigo).isInstanceOf(Validacao.Aceita.class);
            } else {
                assertThat(celular).as("DDD " + codigo).isInstanceOf(Validacao.Recusada.class);
                assertThat(mensagem(celular)).contains("DDD " + codigo);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"(62) 91234-5678", "62 98888-7777", "(11) 99876-5432", "(21) 97654-3210"})
    void celularTemOnzeNumerosEOPrimeiroDepoisDoDddEh9(String entrada) {
        assertThat(Validadores.telefone(entrada, contexto)).isInstanceOf(Validacao.Aceita.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"(62) 81234-5678", "(62) 71234-5678", "(62) 11234-5678", "(62) 51234-5678"})
    void celularComOnzeNumerosSemNoveNaFrenteERecusado(String entrada) {
        assertThat(mensagem(Validadores.telefone(entrada, contexto))).contains("Celular tem 11 números");
    }

    @ParameterizedTest
    @ValueSource(strings = {"(62) 3201-0000", "(11) 4002-8922", "(61) 2345-6789", "(62) 5555-1234"})
    void fixoTemDezNumerosEComecaComDoisATresQuatroOuCinco(String entrada) {
        assertThat(Validadores.telefone(entrada, contexto)).isInstanceOf(Validacao.Aceita.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"(62) 9123-4567", "(62) 6201-0000", "(62) 7201-0000", "(62) 8201-0000", "(62) 1201-0000"})
    void fixoComDezNumerosForaDe2A5ERecusadoEAMensagemOrientaOCelular(String entrada) {
        assertThat(mensagem(Validadores.telefone(entrada, contexto)))
                .contains("Telefone fixo tem 10 números")
                .contains("11 números com 9");
    }

    @ParameterizedTest
    @ValueSource(strings = {"(62) 99999-9999", "(62) 3333-3333", "62 00000-0000", "(11) 4444-4444"})
    void numeroComTodosOsDigitosIguaisEhRecusado(String entrada) {
        assertThat(Validadores.telefone(entrada, contexto)).isInstanceOf(Validacao.Recusada.class);
    }

    @Test
    void numeroPareceValidoSeSoOsPrimeirosDigitosSaoRepetidos() {
        assertThat(Validadores.telefone("(62) 99999-9998", contexto)).isInstanceOf(Validacao.Aceita.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234", "62 9123456", "(62) 9123-45678-9", "999", "", "   "})
    void telefoneComQuantidadeDeNumerosErradaDizOQueFalta(String entrada) {
        assertThat(mensagem(Validadores.telefone(entrada, contexto))).contains("10 números para fixo ou 11 para celular");
    }

    @Test
    void ddiBrasilEOcodigoDoPaisSaoTratadosComoTelefoneNacional() {
        assertThat(Validadores.telefone("+55 (62) 91234-5678", contexto)).isEqualTo(Validacao.aceita("62912345678"));
        assertThat(mensagem(Validadores.telefone("+55 (02) 91234-5678", contexto))).contains("DDD 02");
        assertThat(Validadores.telefone("+351 912 345 678", contexto)).isEqualTo(Validacao.aceita("+351912345678"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-25", "52998224725", "111.444.777-35", "123.456.789-09", "390.533.447-05"})
    void cpfComDigitosVerificadoresCertosEAceitoComOuSemMascara(String entrada) {
        assertThat(Validadores.cpf(entrada, contexto)).isInstanceOf(Validacao.Aceita.class);
    }

    @Test
    void cpfComQuantidadeErradaDeNumerosDizQuantosForamInformados() {
        assertThat(mensagem(Validadores.cpf("529.982.247-2", contexto))).contains("11 números").contains("10");
        assertThat(mensagem(Validadores.cpf("5299822472500", contexto))).contains("13");
        assertThat(mensagem(Validadores.cpf("", contexto))).contains("0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"000.000.000-00", "111.111.111-11", "222.222.222-22", "999.999.999-99"})
    void cpfComTodosOsNumerosIguaisEhRecusadoComMensagemPropria(String entrada) {
        assertThat(mensagem(Validadores.cpf(entrada, contexto))).contains("números são iguais");
    }

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-26", "123.456.789-10", "111.444.777-36", "390.533.447-04"})
    void cpfComDigitoVerificadorErradoEhRecusadoComMensagemPropria(String entrada) {
        assertThat(mensagem(Validadores.cpf(entrada, contexto))).contains("dígitos verificadores não conferem");
    }
}
