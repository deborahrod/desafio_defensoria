package io.github.deborahrod.defensoria.configuracao;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ConfiguracaoTempo {

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }
}
