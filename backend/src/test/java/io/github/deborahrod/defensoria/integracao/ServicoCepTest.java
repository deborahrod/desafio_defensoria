package io.github.deborahrod.defensoria.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ServicoCepTest {

    private static final String CEP = "74000000";
    private static final String VIACEP_ACHADO = """
            {"cep":"74000-000","logradouro":"Rua das Flores Inventadas","complemento":"","bairro":"Setor Central",
             "localidade":"Goiânia","uf":"GO","ibge":"5208707"}""";
    private static final String BRASILAPI_ACHADO = """
            {"cep":"74000000","state":"GO","city":"Goiânia","neighborhood":"Setor Central",
             "street":"Rua das Flores Inventadas","service":"open-cep"}""";

    private static final class RelogioMutavel extends Clock {
        Instant agora = Instant.parse("2026-03-10T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private MockWebServer viacep;
    private MockWebServer brasilapi;
    private ServicoEventos eventos;
    private final RelogioMutavel relogio = new RelogioMutavel();

    @BeforeEach
    void iniciarServidores() throws IOException {
        viacep = new MockWebServer();
        brasilapi = new MockWebServer();
        viacep.start();
        brasilapi.start();
        eventos = mock(ServicoEventos.class);
    }

    @AfterEach
    void pararServidores() throws IOException {
        viacep.shutdown();
        brasilapi.shutdown();
    }

    private ServicoCep servico(Duration timeout, Duration validade, int maximo) {
        var propriedades = new PropriedadesCep(
                timeout, viacep.url("/ws").toString(), brasilapi.url("/api/cep/v2").toString(), maximo, validade);
        var json = new ObjectMapper();
        return new ServicoCep(
                new ClienteViaCep(propriedades, json), new ClienteBrasilApi(propriedades, json), eventos, relogio, propriedades);
    }

    private ServicoCep servico() {
        return servico(Duration.ofSeconds(2), Duration.ofHours(1), 10);
    }

    private static MockResponse json(String corpo) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(corpo);
    }

    private static Endereco achado(ResultadoCep resultado) {
        assertThat(resultado).isInstanceOf(ResultadoCep.Encontrado.class);
        return ((ResultadoCep.Encontrado) resultado).endereco();
    }

    @Test
    void viacepAchaOEnderecoEABrasilApiNemEChamada() throws Exception {
        viacep.enqueue(json(VIACEP_ACHADO));

        var endereco = achado(servico().consultar(CEP));

        assertThat(endereco).isEqualTo(new Endereco(CEP, "Rua das Flores Inventadas", "Setor Central", "Goiânia", "GO"));
        assertThat(viacep.takeRequest().getPath()).isEqualTo("/ws/74000000/json/");
        assertThat(brasilapi.getRequestCount()).isZero();
    }

    @Test
    void naUrlVaiSoOCepESemCabecalhoDeCredencial() throws Exception {
        viacep.enqueue(json(VIACEP_ACHADO));

        servico().consultar(CEP);

        var pedido = viacep.takeRequest();
        assertThat(pedido.getRequestUrl().query()).isNull();
        assertThat(pedido.getHeader("Authorization")).isNull();
        assertThat(pedido.getHeader("Cookie")).isNull();
    }

    @Test
    void viacepNaoConheceOCepEABrasilApiConhece() throws Exception {
        viacep.enqueue(json("{\"erro\":\"true\"}"));
        brasilapi.enqueue(json(BRASILAPI_ACHADO));

        var endereco = achado(servico().consultar(CEP));

        assertThat(endereco.logradouro()).isEqualTo("Rua das Flores Inventadas");
        assertThat(brasilapi.takeRequest().getPath()).isEqualTo("/api/cep/v2/74000000");
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 429})
    void viacepComErroDeServidorFazOFallbackParaABrasilApi(int status) {
        viacep.enqueue(new MockResponse().setResponseCode(status));
        brasilapi.enqueue(json(BRASILAPI_ACHADO));

        assertThat(achado(servico().consultar(CEP)).municipio()).isEqualTo("Goiânia");
    }

    @Test
    void viacepLentoAlemDoTimeoutFazOFallback() {
        viacep.enqueue(json(VIACEP_ACHADO).setBodyDelay(2, TimeUnit.SECONDS));
        brasilapi.enqueue(json(BRASILAPI_ACHADO));

        var inicio = System.nanoTime();
        var resultado = servico(Duration.ofMillis(300), Duration.ofHours(1), 10).consultar(CEP);

        assertThat(achado(resultado).bairro()).isEqualTo("Setor Central");
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void respostaForaDoFormatoNaoEAceitaEPassaParaOProximoProvedor() {
        viacep.enqueue(json("isto não é json"));
        brasilapi.enqueue(json(BRASILAPI_ACHADO));

        assertThat(achado(servico().consultar(CEP)).uf()).isEqualTo("GO");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"cep\":\"74000-000\",\"logradouro\":\"Rua A\",\"bairro\":\"B\",\"localidade\":\"\",\"uf\":\"GO\"}",
        "{\"cep\":\"74000-000\",\"logradouro\":\"Rua A\",\"bairro\":\"B\",\"localidade\":\"Goiânia\",\"uf\":\"Goiás\"}",
        "{\"cep\":\"74000-000\",\"localidade\":123,\"uf\":\"GO\"}",
        "[]",
        ""})
    void respostaSemMunicipioOuUfValidosEDescartada(String corpo) {
        viacep.enqueue(json(corpo));
        brasilapi.enqueue(new MockResponse().setResponseCode(503));

        assertThat(servico().consultar(CEP)).isInstanceOf(ResultadoCep.Indisponivel.class);
    }

    @Test
    void cepDeMunicipioPequenoSemRuaNemBairroEAceito() {
        viacep.enqueue(json("{\"cep\":\"76000-000\",\"logradouro\":\"\",\"bairro\":\"\",\"localidade\":\"Cidade Pequena\",\"uf\":\"go\"}"));

        var endereco = achado(servico().consultar("76000000"));

        assertThat(endereco.logradouro()).isEmpty();
        assertThat(endereco.uf()).isEqualTo("GO");
    }

    @Test
    void textoDoProvedorChegaLimpoSemControleEEspacosSobrando() {
        viacep.enqueue(json("{\"logradouro\":\"  Rua\\t  das \\n Flores  \",\"bairro\":\"Setor\\u0000 Central\",\"localidade\":\"Goiânia\",\"uf\":\"GO\"}"));

        var endereco = achado(servico().consultar(CEP));

        assertThat(endereco.logradouro()).isEqualTo("Rua das Flores");
        assertThat(endereco.bairro()).isEqualTo("Setor Central");
    }

    @Test
    void osDoisProvedoresDizemQueONaoExisteResultaEmNaoEncontrado() {
        viacep.enqueue(json("{\"erro\":\"true\"}"));
        brasilapi.enqueue(new MockResponse().setResponseCode(404).setBody("{\"message\":\"CEP não encontrado\"}"));

        assertThat(servico().consultar(CEP)).isInstanceOf(ResultadoCep.NaoEncontrado.class);
    }

    @Test
    void umDizQueNaoExisteEOOutroEstaForaDoArContaComoNaoEncontrado() {
        viacep.enqueue(new MockResponse().setResponseCode(400));
        brasilapi.enqueue(new MockResponse().setResponseCode(500));

        assertThat(servico().consultar(CEP)).isInstanceOf(ResultadoCep.NaoEncontrado.class);
    }

    @Test
    void osDoisProvedoresForaDoArResultaEmIndisponivel() {
        viacep.enqueue(new MockResponse().setResponseCode(500));
        brasilapi.enqueue(new MockResponse().setResponseCode(500));

        assertThat(servico().consultar(CEP)).isInstanceOf(ResultadoCep.Indisponivel.class);
    }

    @Test
    void provedorQueNaoEstaNoArNaoBloqueiaAlemDoTimeout() throws IOException {
        viacep.shutdown();
        brasilapi.shutdown();

        var inicio = System.nanoTime();
        var resultado = servico(Duration.ofMillis(500), Duration.ofHours(1), 10).consultar(CEP);

        assertThat(resultado).isInstanceOf(ResultadoCep.Indisponivel.class);
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void aSegundaConsultaDoMesmoCepVemDoCacheSemChamarProvedor() {
        viacep.enqueue(json(VIACEP_ACHADO));
        var servico = servico();

        var primeira = servico.consultar(CEP);
        var segunda = servico.consultar(CEP);

        assertThat(segunda).isEqualTo(primeira);
        assertThat(viacep.getRequestCount()).isEqualTo(1);
        verify(eventos).registrar(eq("CONSULTA_CEP"), eq("CACHE"), isNull(), eq("cache"), anyLong());
    }

    @Test
    void cepNaoEncontradoTambemFicaNoCache() {
        viacep.enqueue(json("{\"erro\":\"true\"}"));
        brasilapi.enqueue(new MockResponse().setResponseCode(404));
        var servico = servico();

        servico.consultar(CEP);
        assertThat(servico.consultar(CEP)).isInstanceOf(ResultadoCep.NaoEncontrado.class);

        assertThat(viacep.getRequestCount()).isEqualTo(1);
        assertThat(brasilapi.getRequestCount()).isEqualTo(1);
    }

    @Test
    void falhaDeRedeNaoEntraNoCacheEAProximaConsultaTentaDeNovo() {
        viacep.enqueue(new MockResponse().setResponseCode(500));
        brasilapi.enqueue(new MockResponse().setResponseCode(500));
        viacep.enqueue(json(VIACEP_ACHADO));
        var servico = servico();

        assertThat(servico.consultar(CEP)).isInstanceOf(ResultadoCep.Indisponivel.class);
        assertThat(servico.consultar(CEP)).isInstanceOf(ResultadoCep.Encontrado.class);
    }

    @Test
    void oCacheExpiraDepoisDaValidade() {
        viacep.enqueue(json(VIACEP_ACHADO));
        viacep.enqueue(json(VIACEP_ACHADO));
        var servico = servico(Duration.ofSeconds(2), Duration.ofMinutes(10), 10);

        servico.consultar(CEP);
        relogio.agora = relogio.agora.plus(Duration.ofMinutes(9));
        servico.consultar(CEP);
        assertThat(viacep.getRequestCount()).isEqualTo(1);

        relogio.agora = relogio.agora.plus(Duration.ofMinutes(2));
        servico.consultar(CEP);
        assertThat(viacep.getRequestCount()).isEqualTo(2);
    }

    @Test
    void oCacheTemTamanhoLimitadoEDescartaOMenosUsado() {
        for (var i = 0; i < 4; i++) {
            viacep.enqueue(json(VIACEP_ACHADO));
        }
        var servico = servico(Duration.ofSeconds(2), Duration.ofHours(1), 2);

        servico.consultar("74000001");
        servico.consultar("74000002");
        servico.consultar("74000003");
        assertThat(viacep.getRequestCount()).isEqualTo(3);

        servico.consultar("74000003");
        servico.consultar("74000002");
        assertThat(viacep.getRequestCount()).isEqualTo(3);

        servico.consultar("74000001");
        assertThat(viacep.getRequestCount()).isEqualTo(4);
    }

    @Test
    void oEventoTecnicoNaoCarregaOCep() {
        viacep.enqueue(json(VIACEP_ACHADO));

        servico().consultar(CEP);

        verify(eventos).registrar(eq("CONSULTA_CEP"), eq("ENCONTRADO"), isNull(), eq("viacep"), anyLong());
        org.mockito.Mockito.verifyNoMoreInteractions(eventos);
        verify(eventos, org.mockito.Mockito.never()).registrar(any(), any(), any(), eq(CEP), any());
    }

    @Test
    void propriedadesUsamPadroesQuandoVazias() {
        var padrao = new PropriedadesCep(null, " ", null, 0, null);

        assertThat(padrao.timeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(padrao.viacepUrl()).isEqualTo("https://viacep.com.br/ws");
        assertThat(padrao.brasilapiUrl()).isEqualTo("https://brasilapi.com.br/api/cep/v2");
        assertThat(padrao.cacheMaximo()).isEqualTo(500);
        assertThat(padrao.cacheValidade()).isEqualTo(Duration.ofHours(6));
    }
}
