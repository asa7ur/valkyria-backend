package org.iesalixar.daw2.GarikAsatryan.valkyria.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DotenvConfig {
    private static final Logger logger = LoggerFactory.getLogger(DotenvConfig.class);

    static {
        try {
            // En producción las variables pueden venir del entorno sin fichero .env
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            dotenv.entries(Dotenv.Filter.DECLARED_IN_ENV_FILE).forEach(entry -> {
                // Una variable de entorno real tiene prioridad sobre el .env (como hacía antes)
                if (System.getenv(entry.getKey()) != null) {
                    return;
                }
                System.setProperty(entry.getKey(), entry.getValue());
                // Solo el nombre: los valores son contraseñas y claves
                logger.debug("Variable cargada desde .env: {}", entry.getKey());
            });
            logger.info("Variables de entorno cargadas desde .env (si existe)");
        } catch (Exception e) {
            logger.error("No se pudo leer el fichero .env: {}", e.getMessage());
        }
    }
}
