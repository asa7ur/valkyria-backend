package org.iesalixar.daw2.GarikAsatryan.valkyria.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Códigos de un solo uso para terminar el login con Google.
 * <p>
 * Tras autenticar con Google, el backend redirige al frontend con un código en la URL (no con el JWT,
 * que quedaría en el historial del navegador y en logs). El frontend lo canjea por el JWT con un POST.
 * <p>
 * Se guardan en memoria: con varias instancias del backend haría falta un almacén compartido.
 */
@Component
public class OAuth2LoginCodeStore {

    static final Duration CODE_TTL = Duration.ofSeconds(60);

    private final SecureRandom secureRandom = new SecureRandom();

    private final Cache<String, String> emailsByCode = Caffeine.newBuilder()
            .expireAfterWrite(CODE_TTL)
            .maximumSize(10_000)
            .build();

    public String issue(String email) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        emailsByCode.put(code, email);
        return code;
    }

    /**
     * Devuelve el email asociado y elimina el código, de forma que solo se puede canjear una vez.
     */
    public Optional<String> consume(String code) {
        return Optional.ofNullable(emailsByCode.asMap().remove(code));
    }
}
