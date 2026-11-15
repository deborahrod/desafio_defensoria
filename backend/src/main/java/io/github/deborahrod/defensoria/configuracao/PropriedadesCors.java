package io.github.deborahrod.defensoria.configuracao;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cadastro.cors")
public record PropriedadesCors(List<String> origens) {

    public PropriedadesCors {
        origens = origens == null ? List.of() : origens.stream().filter(origem -> !origem.isBlank()).toList();
    }
}
