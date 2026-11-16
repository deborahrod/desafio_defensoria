package io.github.deborahrod.defensoria.configuracao;

import io.github.deborahrod.defensoria.cadastro.PropriedadesSessao;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Com o perfil {@code prod} ativo, a aplicação não sobe com os padrões de demonstração: cookie de sessão
 * sem {@code Secure} ou a senha de banco que vem de fábrica. Os valores nunca aparecem na mensagem.
 */
@Component
@Profile("prod")
class VerificacaoDeProducao {

    static final String SENHA_PADRAO = "cadastro_local";

    VerificacaoDeProducao(PropriedadesSessao sessao, @Value("${spring.datasource.password:}") String senhaDoBanco) {
        var problemas = problemas(sessao.cookieSeguro(), senhaDoBanco);
        if (!problemas.isEmpty()) {
            throw new IllegalStateException(
                    "Configuração insegura para o perfil prod: " + String.join("; ", problemas) + ".");
        }
    }

    static List<String> problemas(boolean cookieSeguro, String senhaDoBanco) {
        var problemas = new ArrayList<String>();
        if (!cookieSeguro) {
            problemas.add("defina SESSAO_COOKIE_SEGURO=true para que o cookie de sessão só trafegue por HTTPS");
        }
        if (senhaDoBanco == null || senhaDoBanco.isBlank() || SENHA_PADRAO.equals(senhaDoBanco)) {
            problemas.add("defina DB_PASSWORD com uma senha própria, diferente da padrão de demonstração");
        }
        return problemas;
    }
}
