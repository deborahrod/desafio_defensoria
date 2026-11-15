package io.github.deborahrod.defensoria.cadastro;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public record Contato(
        @Enumerated(EnumType.STRING) FormaContato formaContato,
        String telefone,
        String email,
        String referenciaNome,
        String referenciaVinculo,
        String referenciaCanal) {

    public static Contato indisponivel() {
        return new Contato(FormaContato.INDISPONIVEL, null, null, null, null, null);
    }
}
