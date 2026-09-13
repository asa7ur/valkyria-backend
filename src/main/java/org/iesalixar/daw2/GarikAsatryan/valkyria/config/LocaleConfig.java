package org.iesalixar.daw2.GarikAsatryan.valkyria.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

@Configuration
public class LocaleConfig {

    private static final Locale SPANISH = Locale.of("es");

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(SPANISH, Locale.ENGLISH));
        resolver.setDefaultLocale(SPANISH);
        return resolver;
    }
}
