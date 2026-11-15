package io.github.deborahrod.defensoria.configuracao;

import io.github.deborahrod.defensoria.cadastro.SessaoAtualResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class ConfiguracaoWeb implements WebMvcConfigurer {

    private final SessaoAtualResolver sessaoAtual;

    ConfiguracaoWeb(SessaoAtualResolver sessaoAtual) {
        this.sessaoAtual = sessaoAtual;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvedores) {
        resolvedores.add(sessaoAtual);
    }
}
