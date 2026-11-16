package io.github.deborahrod.defensoria.conversa;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastro;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

/**
 * Lê os mesmos casos que o frontend (testes-compartilhados/validacao.json). Se o servidor e a interface
 * passarem a discordar sobre CPF ou telefone, um dos dois testes falha.
 */
class VetoresCompartilhadosTest {

    private static final Path ARQUIVO = Path.of("..", "testes-compartilhados", "validacao.json");

    private final ContextoResposta contexto = new ContextoResposta(
            new RascunhoCadastro(UUID.randomUUID(), Instant.parse("2026-11-15T12:00:00Z")), LocalDate.of(2026, 11, 15));

    private static JsonNode vetores() throws IOException {
        return new ObjectMapper().readTree(Files.readString(ARQUIVO));
    }

    private void conferir(String campo, BiFunction<String, ContextoResposta, Validacao> regra) throws IOException {
        var falhas = new ArrayList<String>();
        var casos = vetores().get(campo);
        assertThat(casos.size()).as("casos de " + campo).isGreaterThan(5);
        for (var caso : casos) {
            var entrada = caso.get("entrada").asText();
            var resultado = regra.apply(entrada, contexto);
            if (caso.get("valido").asBoolean()) {
                if (!(resultado instanceof Validacao.Aceita)) {
                    falhas.add("devia aceitar: '" + entrada + "'");
                }
            } else if (!(resultado instanceof Validacao.Recusada recusada)) {
                falhas.add("devia recusar: '" + entrada + "'");
            } else if (!recusada.mensagem().contains(caso.get("mensagemContem").asText())) {
                falhas.add("mensagem de '" + entrada + "' não contém '" + caso.get("mensagemContem").asText()
                        + "': " + recusada.mensagem());
            }
        }
        assertThat(falhas).isEmpty();
    }

    @Test
    void cpfNoServidorConcordaComOsVetoresCompartilhados() throws IOException {
        conferir("cpf", Validadores::cpf);
    }

    @Test
    void telefoneNoServidorConcordaComOsVetoresCompartilhados() throws IOException {
        conferir("telefone", Validadores::telefone);
    }
}
