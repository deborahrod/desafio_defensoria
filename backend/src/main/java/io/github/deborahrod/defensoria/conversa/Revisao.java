package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.conversa.EstadoConversa.SecaoResumo;
import java.util.List;

/** O que a pessoa confere antes de confirmar, com a versão que a confirmação deve citar. */
public record Revisao(int versao, List<SecaoResumo> resumo, List<String> pendencias) {
}
