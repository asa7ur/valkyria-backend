package org.iesalixar.daw2.GarikAsatryan.Valkyria;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mariadb.MariaDBContainer;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Misma versión que la BD de desarrollo (docker-compose)
    static final String MARIADB_IMAGE = "mariadb:12.2";

    @Bean
    @ServiceConnection
    MariaDBContainer mariaDbContainer() {
        return new MariaDBContainer(MARIADB_IMAGE);
    }

    /**
     * Cada petición de MockMvc sale de una IP distinta para que los tests no agoten los límites
     * por IP (RateLimitInterceptor). Los tests de esos límites fijan la IP con .with(...).
     */
    @Bean
    MockMvcBuilderCustomizer distinctClientIpPerRequest() {
        AtomicInteger counter = new AtomicInteger();
        return builder -> builder.defaultRequest(get("/").with(request -> {
            int n = counter.incrementAndGet();
            request.setRemoteAddr("10." + ((n >> 16) & 255) + "." + ((n >> 8) & 255) + "." + (n & 255));
            return request;
        }));
    }
}
