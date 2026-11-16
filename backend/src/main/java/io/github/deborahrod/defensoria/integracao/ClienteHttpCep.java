package io.github.deborahrod.defensoria.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Parte comum dos provedores: timeout curto, nenhum dado além do CEP na URL e leitura tolerante do JSON. */
abstract class ClienteHttpCep implements ProvedorCep {

    private static final int TAMANHO_MAXIMO = 200;

    private final RestClient http;
    private final ObjectMapper json;

    ClienteHttpCep(String urlBase, PropriedadesCep propriedades, ObjectMapper json) {
        var cliente = HttpClient.newBuilder().connectTimeout(propriedades.timeout()).build();
        var fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(propriedades.timeout());
        this.http = RestClient.builder().baseUrl(urlBase).requestFactory(fabrica).build();
        this.json = json;
    }

    /** Vazio quando o provedor diz que o CEP não existe (404 ou 400 de formato). */
    protected Optional<JsonNode> ler(String caminho) throws FalhaCepException {
        try {
            var corpo = http.get().uri(caminho).header("Accept", "application/json").retrieve().body(String.class);
            if (corpo == null || corpo.isBlank()) {
                throw new FalhaCepException("resposta vazia");
            }
            var no = json.readTree(corpo);
            if (!no.isObject()) {
                throw new FalhaCepException("resposta fora do formato");
            }
            return Optional.of(no);
        } catch (RestClientResponseException e) {
            var status = e.getStatusCode().value();
            if (status == 404 || status == 400) {
                return Optional.empty();
            }
            throw new FalhaCepException("status " + status, e);
        } catch (RestClientException | IOException e) {
            throw new FalhaCepException("falha de comunicação: " + e.getClass().getSimpleName(), e);
        }
    }

    /** Texto do provedor limpo: sem controle, sem espaço sobrando e com tamanho limitado. */
    protected static String texto(JsonNode no, String campo) {
        var valor = no.path(campo);
        if (!valor.isTextual()) {
            return "";
        }
        var limpo = valor.asText().replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").strip();
        return limpo.length() > TAMANHO_MAXIMO ? limpo.substring(0, TAMANHO_MAXIMO) : limpo;
    }

    /** Monta o endereço ou rejeita a resposta quando faltam município ou UF, que sempre vêm em CEP real. */
    protected static Endereco endereco(String cep, String logradouro, String bairro, String municipio, String uf)
            throws FalhaCepException {
        var sigla = uf.toUpperCase(Locale.ROOT);
        if (municipio.isEmpty() || !sigla.matches("[A-Z]{2}")) {
            throw new FalhaCepException("resposta sem município ou UF");
        }
        return new Endereco(cep, logradouro, bairro, municipio, sigla);
    }
}
