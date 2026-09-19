package org.iesalixar.daw2.GarikAsatryan.valkyria.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Los datos de prueba (usuarios con contraseñas conocidas) nunca deben cargarse sin pedirlo explícitamente.
 */
class FlywayProfilesTest {

    private static final String LOCATIONS = "spring.flyway.locations";

    @Test
    void defaultConfiguration_doesNotLoadSeedData() throws IOException {
        assertEquals("classpath:db/migration", load("application.properties").getProperty(LOCATIONS));
    }

    @Test
    void prodProfile_doesNotReEnableSeedData() throws IOException {
        String locations = load("application-prod.properties").getProperty(LOCATIONS);
        assertTrue(locations == null || !locations.contains("db/seed"));
    }

    @Test
    void devProfile_loadsSeedData() throws IOException {
        assertTrue(load("application-dev.properties").getProperty(LOCATIONS).contains("classpath:db/seed"));
    }

    private Properties load(String file) throws IOException {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource(file));
    }
}
