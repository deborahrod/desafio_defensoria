package io.github.deborahrod.defensoria;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/** Base dos testes que precisam do banco: um único PostgreSQL para toda a execução. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public abstract class TesteIntegracao {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
