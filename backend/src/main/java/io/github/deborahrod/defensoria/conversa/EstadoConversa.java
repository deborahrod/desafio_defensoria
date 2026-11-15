package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.cadastro.EstadoSessao;
import io.github.deborahrod.defensoria.cadastro.ModoInteracao;
import java.time.Instant;
import java.util.List;

/** Retrato da conversa devolvido a cada turno: estado, próxima pergunta e o que já foi aceito. */
public record EstadoConversa(
        EstadoSessao estado,
        ModoInteracao modoInteracao,
        int versao,
        ProximaPergunta proximaPergunta,
        Progresso progresso,
        List<SecaoResumo> resumo,
        List<String> pendencias,
        List<String> avisos,
        Comprovante comprovante,
        Instant expiraEm,
        long minutosDeInatividade) {

    public record ProximaPergunta(
            String campo,
            String texto,
            String ajuda,
            TipoEntrada tipoEntrada,
            List<Opcao> opcoes,
            Integer tamanhoMaximo) {
    }

    /** Posição entre as seções do cadastro. Não é percentual: o caminho muda conforme as respostas. */
    public record Progresso(int etapa, int total, String secao) {
    }

    public record SecaoResumo(String titulo, List<ItemResumo> itens) {
    }

    public record ItemResumo(String campo, String rotulo, String valor, SituacaoItem situacao) {
    }

    public enum SituacaoItem {
        INFORMADO,
        NAO_INFORMADO,
        OPCIONAL_NAO_PREENCHIDO
    }
}
