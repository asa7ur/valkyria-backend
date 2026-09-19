package org.iesalixar.daw2.GarikAsatryan.valkyria.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

@Configuration
public class LocaleConfig {

    public static final Locale SPANISH = Locale.of("es");
    public static final List<Locale> SUPPORTED_LOCALES = List.of(SPANISH, Locale.ENGLISH);

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(SUPPORTED_LOCALES);
        resolver.setDefaultLocale(SPANISH);
        return resolver;
    }

    /**
     * Idioma soportado que corresponde a un código ("es", "en"...); español si no se reconoce.
     * Para textos generados fuera de una petición (tareas en segundo plano), donde el idioma
     * por defecto sería el del sistema operativo.
     */
    public static Locale supportedOrDefault(String language) {
        return SUPPORTED_LOCALES.stream()
                .filter(locale -> locale.getLanguage().equalsIgnoreCase(language))
                .findFirst()
                .orElse(SPANISH);
    }
}
