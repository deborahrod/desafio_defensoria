package io.github.deborahrod.defensoria.integracao;

import static io.github.deborahrod.defensoria.integracao.FalhaInterpretacaoException.Categoria.RESPOSTA_INVALIDA;

import io.github.deborahrod.defensoria.integracao.PedidoInterpretacao.CampoPermitido;
import io.github.deborahrod.defensoria.integracao.ResultadoInterpretacao.Candidato;
import io.github.deborahrod.defensoria.integracao.ResultadoInterpretacao.Operacao;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Confere a saída do modelo contra o contrato. Qualquer desvio descarta a resposta inteira. */
final class ValidadorSaida {

    private static final int TAMANHO_MAXIMO_VALOR = 200;

    record Saida(Operacao operacao, List<CandidatoSaida> candidatos, Boolean precisaEsclarecimento) {
    }

    record CandidatoSaida(String campo, String valor, String trechoOrigem) {
    }

    private ValidadorSaida() {
    }

    static ResultadoInterpretacao validar(Saida saida, PedidoInterpretacao pedido) {
        if (saida == null || saida.operacao() == null || saida.candidatos() == null || saida.precisaEsclarecimento() == null) {
            throw recusa();
        }
        if (saida.operacao() == Operacao.FORA_DE_ESCOPO && !saida.candidatos().isEmpty()) {
            throw recusa();
        }
        var permitidos = pedido.campos().stream().map(CampoPermitido::id).collect(java.util.stream.Collectors.toSet());
        var resposta = normalizar(pedido.resposta());
        var vistos = new HashSet<String>();
        var candidatos = saida.candidatos().stream()
                .map(candidato -> conferir(candidato, permitidos, resposta, vistos))
                .toList();
        return new ResultadoInterpretacao(saida.operacao(), candidatos, saida.precisaEsclarecimento());
    }

    private static Candidato conferir(CandidatoSaida candidato, Set<String> permitidos, String resposta, Set<String> vistos) {
        if (candidato == null || candidato.campo() == null || !permitidos.contains(candidato.campo())) {
            throw recusa();
        }
        if (!vistos.add(candidato.campo())) {
            throw recusa();
        }
        var valor = candidato.valor();
        if (valor == null || valor.isBlank() || valor.length() > TAMANHO_MAXIMO_VALOR
                || valor.codePoints().anyMatch(Character::isISOControl)) {
            throw recusa();
        }
        // O trecho de origem precisa existir de verdade na resposta da pessoa.
        var trecho = candidato.trechoOrigem() == null ? "" : normalizar(candidato.trechoOrigem());
        if (trecho.isEmpty() || !resposta.contains(trecho)) {
            throw recusa();
        }
        return new Candidato(candidato.campo(), valor.strip(), candidato.trechoOrigem().strip());
    }

    static String normalizar(String texto) {
        var semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    private static FalhaInterpretacaoException recusa() {
        return new FalhaInterpretacaoException(RESPOSTA_INVALIDA);
    }
}
