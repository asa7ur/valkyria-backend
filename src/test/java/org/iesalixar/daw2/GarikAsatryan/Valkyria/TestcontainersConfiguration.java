package org.iesalixar.daw2.GarikAsatryan.Valkyria;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mariadb.MariaDBContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Misma versión que la BD de desarrollo (docker-compose)
    static final String MARIADB_IMAGE = "mariadb:12.2";

    @Bean
    @ServiceConnection
    MariaDBContainer mariaDbContainer() {
        return new MariaDBContainer(MARIADB_IMAGE);
    }
}
