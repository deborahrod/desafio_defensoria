package io.github.deborahrod.defensoria;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/** Base dos testes que precisam do banco: um único PostgreSQL para toda a execução. */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "cadastro.cors.origens=http://localhost:5173",
            // Limites folgados: os testes de limite sobrescrevem estes valores.
            "cadastro.limites.respostas-por-sessao=1000",
            "cadastro.limites.respostas-por-minuto=1000",
            "cadastro.limites.requisicoes-por-origem-por-minuto=100000"
        })
@AutoConfigureMockMvc
@Import(TesteIntegracao.Configuracao.class)
public abstract class TesteIntegracao {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected RelogioDeTeste relogio;

    @Autowired
    protected InterpretadorFalso interpretador;

    @Autowired
    protected ConsultaCepFalsa cep;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void comecarDoZero() {
        relogio.reiniciar();
        interpretador.reiniciar();
        cep.reiniciar();
        jdbc.execute("""
                TRUNCATE confirmacao_cadastro, declaracao_socioeconomica, representacao_declarada,
                    pessoa_cadastro, mensagem_processada, rascunho_cadastro, sessao_cadastro, evento_tecnico
                    RESTART IDENTITY CASCADE""");
    }

    protected Navegador novoNavegador() {
        return new Navegador(mvc);
    }

    @TestConfiguration
    static class Configuracao {

        @Bean
        @Primary
        RelogioDeTeste relogioDeTeste() {
            return new RelogioDeTeste();
        }

        @Bean
        @Primary
        InterpretadorFalso interpretadorFalso() {
            return new InterpretadorFalso();
        }

        @Bean
        @Primary
        ConsultaCepFalsa consultaCepFalsa() {
            return new ConsultaCepFalsa();
        }
    }
}
