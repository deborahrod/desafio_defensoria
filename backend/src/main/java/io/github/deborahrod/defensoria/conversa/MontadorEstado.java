package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.cadastro.Campo;
import io.github.deborahrod.defensoria.cadastro.MotivoAusencia;
import io.github.deborahrod.defensoria.cadastro.PropriedadesSessao;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastro;
import io.github.deborahrod.defensoria.cadastro.SessaoCadastro;
import io.github.deborahrod.defensoria.conversa.EstadoConversa.ItemResumo;
import io.github.deborahrod.defensoria.conversa.EstadoConversa.Progresso;
import io.github.deborahrod.defensoria.conversa.EstadoConversa.ProximaPergunta;
import io.github.deborahrod.defensoria.conversa.EstadoConversa.SecaoResumo;
import io.github.deborahrod.defensoria.conversa.EstadoConversa.SituacaoItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MontadorEstado {

    private final RoteiroCadastro roteiro;
    private final PropriedadesSessao propriedades;

    public MontadorEstado(RoteiroCadastro roteiro, PropriedadesSessao propriedades) {
        this.roteiro = roteiro;
        this.propriedades = propriedades;
    }

    public EstadoConversa montar(SessaoCadastro sessao, RascunhoCadastro rascunho, List<String> avisos) {
        var proxima = roteiro.proxima(rascunho);
        return new EstadoConversa(
                sessao.getEstado(),
                sessao.getModoInteracao(),
                rascunho.getVersao(),
                proxima.map(pergunta -> apresentar(pergunta, rascunho)).orElse(null),
                progresso(rascunho, proxima),
                resumo(rascunho),
                pendencias(rascunho),
                avisos,
                sessao.getExpiraEm(),
                propriedades.expiracao().toMinutes());
    }

    public List<String> pendencias(RascunhoCadastro rascunho) {
        var pendencias = new ArrayList<String>();
        roteiro.aplicaveis(rascunho).forEach(pergunta -> {
            var campo = pergunta.campo();
            var motivo = rascunho.ausencia(campo);
            if (motivo.isPresent() && pendente(motivo.get())) {
                pendencias.add(pergunta.rotulo() + ": " + descrever(motivo.get()));
            } else if (campo == Campo.SITUACAO_CPF && !rascunho.tem(campo, "INFORMADO")) {
                rascunho.valor(campo).ifPresent(valor -> pendencias.add("CPF: " + pergunta.exibir(valor, rascunho)));
            } else if (campo == Campo.FORMA_CONTATO && rascunho.tem(campo, "INDISPONIVEL")) {
                pendencias.add("Contato: não há contato disponível");
            }
        });
        return pendencias;
    }

    private ProximaPergunta apresentar(Pergunta pergunta, RascunhoCadastro rascunho) {
        return new ProximaPergunta(
                pergunta.campo().id(),
                pergunta.texto(RoteiroCadastro.paraOutraPessoa(rascunho)),
                pergunta.ajuda(),
                pergunta.tipoEntrada(),
                pergunta.opcoes(rascunho),
                pergunta.tamanhoMaximo());
    }

    private Progresso progresso(RascunhoCadastro rascunho, Optional<Pergunta> proxima) {
        var secoes = roteiro.aplicaveis(rascunho).map(Pergunta::secao).distinct().toList();
        return proxima
                .map(pergunta -> new Progresso(
                        secoes.indexOf(pergunta.secao()) + 1, secoes.size(), pergunta.secao().titulo()))
                .orElseGet(() -> new Progresso(secoes.size(), secoes.size(), "Revisão"));
    }

    private List<SecaoResumo> resumo(RascunhoCadastro rascunho) {
        var porSecao = new LinkedHashMap<Secao, List<ItemResumo>>();
        roteiro.aplicaveis(rascunho)
                .filter(pergunta -> rascunho.respondido(pergunta.campo()))
                .forEach(pergunta -> porSecao
                        .computeIfAbsent(pergunta.secao(), secao -> new ArrayList<>())
                        .add(item(pergunta, rascunho)));
        return porSecao.entrySet().stream()
                .map(secao -> new SecaoResumo(secao.getKey().titulo(), secao.getValue()))
                .toList();
    }

    private ItemResumo item(Pergunta pergunta, RascunhoCadastro rascunho) {
        var campo = pergunta.campo();
        return rascunho.valor(campo)
                .map(valor -> new ItemResumo(
                        campo.id(), pergunta.rotulo(), pergunta.exibir(valor, rascunho), SituacaoItem.INFORMADO))
                .orElseGet(() -> {
                    var motivo = rascunho.ausencia(campo).orElseThrow();
                    return new ItemResumo(campo.id(), pergunta.rotulo(), descrever(motivo), situacao(motivo));
                });
    }

    private static boolean pendente(MotivoAusencia motivo) {
        return situacao(motivo) == SituacaoItem.NAO_INFORMADO;
    }

    private static SituacaoItem situacao(MotivoAusencia motivo) {
        return switch (motivo) {
            case OPCIONAL_NAO_PREENCHIDO -> SituacaoItem.OPCIONAL_NAO_PREENCHIDO;
            case SEM_NUMERO -> SituacaoItem.INFORMADO;
            default -> SituacaoItem.NAO_INFORMADO;
        };
    }

    private static String descrever(MotivoAusencia motivo) {
        return switch (motivo) {
            case NAO_POSSUI -> "Não possui";
            case DESCONHECIDO -> "Não soube informar";
            case NAO_APRESENTADO -> "Não apresentado";
            case INDISPONIVEL -> "Não disponível";
            case SEM_NUMERO -> "Sem número";
            case OPCIONAL_NAO_PREENCHIDO -> "Não preenchido";
        };
    }
}
