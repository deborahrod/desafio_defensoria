package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.cadastro.RascunhoCadastro;
import java.time.LocalDate;

public record ContextoResposta(RascunhoCadastro rascunho, LocalDate hoje) {
}
