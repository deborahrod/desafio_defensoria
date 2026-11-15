package io.github.deborahrod.defensoria.cadastro;

import java.time.Instant;

public record SessaoResposta(
        EstadoSessao estado, ModoInteracao modoInteracao, Instant expiraEm, long minutosDeInatividade) {

    static SessaoResposta de(SessaoCadastro sessao, PropriedadesSessao propriedades) {
        return new SessaoResposta(
                sessao.getEstado(),
                sessao.getModoInteracao(),
                sessao.getExpiraEm(),
                propriedades.expiracao().toMinutes());
    }
}
