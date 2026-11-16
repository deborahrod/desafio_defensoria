package io.github.deborahrod.defensoria.integracao;

import static io.github.deborahrod.defensoria.integracao.FalhaInterpretacaoException.Categoria.RESPOSTA_INVALIDA;

import io.github.deborahrod.defensoria.integracao.PedidoInterpretacao.CampoPermitido;
import io.github.deborahrod.defensoria.integracao.PedidoInterpretacao.Topico;
import io.github.deborahrod.defensoria.integracao.ResultadoInterpretacao.Candidato;
import io.github.deborahrod.defensoria.integracao.ResultadoInterpretacao.Operacao;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Confere a saída do modelo contra o contrato. Qualquer desvio descarta a resposta inteira. */
final class ValidadorSaida {

    private static final int TAMANHO_MAXIMO_VALOR = 200;

    record Saida(Operacao operacao, List<CandidatoSaida> candidatos, Boolean precisaEsclarecimento, String topico) {
    }

    record CandidatoSaida(String campo, String valor, String trechoOrigem) {
    }

    private ValidadorSaida() {
    }

    static ResultadoInterpretacao validar(Saida saida, PedidoInterpretacao pedido) {
        if (saida == null || saida.operacao() == null || saida.candidatos() == null || saida.precisaEsclarecimento() == null) {
            throw recusa("propriedade obrigatória ausente");
        }
        conferirTopico(saida, pedido.topicos());
        if ((saida.operacao() == Operacao.FORA_DE_ESCOPO || saida.operacao() == Operacao.AJUDA) && !saida.candidatos().isEmpty()) {
            throw recusa("candidatos em operação que não os admite");
        }
        var campos = pedido.campos().stream().collect(Collectors.toMap(CampoPermitido::id, c -> c));
        var resposta = NormalizacaoDeTexto.normalizar(pedido.resposta());
        var vistos = new HashSet<String>();
        var candidatos = saida.candidatos().stream()
                .map(candidato -> conferir(candidato, campos, resposta, vistos))
                .toList();
        return new ResultadoInterpretacao(saida.operacao(), candidatos, saida.precisaEsclarecimento(), saida.topico());
    }

    /** AJUDA exige um tópico do catálogo enviado no pedido. Nas demais operações o tópico não existe. */
    private static void conferirTopico(Saida saida, List<Topico> topicos) {
        if (saida.operacao() == Operacao.AJUDA) {
            Set<String> ids = topicos.stream().map(Topico::id).collect(Collectors.toSet());
            if (saida.topico() == null || !ids.contains(saida.topico())) {
                throw recusa("tópico fora do catálogo");
            }
        } else if (saida.topico() != null) {
            throw recusa("tópico fora da operação de ajuda");
        }
    }

    private static Candidato conferir(
            CandidatoSaida candidato, Map<String, CampoPermitido> campos, String resposta, Set<String> vistos) {
        if (candidato == null || candidato.campo() == null || !campos.containsKey(candidato.campo())) {
            throw recusa("campo fora da lista do turno");
        }
        if (!vistos.add(candidato.campo())) {
            throw recusa("campo repetido");
        }
        var valor = candidato.valor();
        if (valor == null || valor.isBlank() || valor.length() > TAMANHO_MAXIMO_VALOR
                || valor.codePoints().anyMatch(Character::isISOControl)) {
            throw recusa("valor vazio, grande demais ou com caractere de controle: " + candidato.campo());
        }
        // Campo de escolha fechada: o valor é um dos ids oferecidos, nada além.
        var opcoes = campos.get(candidato.campo()).opcoes();
        if (!opcoes.isEmpty() && !opcoes.contains(valor.strip())) {
            throw recusa("valor fora das opções: " + candidato.campo());
        }
        // O trecho de origem precisa existir de verdade na resposta da pessoa.
        var trecho = candidato.trechoOrigem() == null ? "" : NormalizacaoDeTexto.normalizar(candidato.trechoOrigem());
        if (trecho.isEmpty() || !resposta.contains(trecho)) {
            throw recusa("trecho de origem que não está na resposta: " + candidato.campo());
        }
        return new Candidato(candidato.campo(), valor.strip(), candidato.trechoOrigem().strip());
    }

    static String normalizar(String texto) {
        return NormalizacaoDeTexto.normalizar(texto);
    }

    private static FalhaInterpretacaoException recusa(String regra) {
        return new FalhaInterpretacaoException(RESPOSTA_INVALIDA, regra);
    }
}
