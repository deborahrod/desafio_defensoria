package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.cadastro.ConfirmacaoCadastro;
import java.time.Instant;
import java.util.UUID;

/** Identifica a gravação do protótipo. Não é protocolo oficial nem dá acesso ao cadastro. */
public record Comprovante(UUID identificador, Instant confirmadoEm, boolean comPendencias) {

    static Comprovante de(ConfirmacaoCadastro confirmacao) {
        return new Comprovante(confirmacao.getId(), confirmacao.getConfirmadoEm(), confirmacao.isComPendencias());
    }
}
