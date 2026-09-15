package org.iesalixar.daw2.GarikAsatryan.valkyria.components;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Límites de peticiones con token buckets (Bucket4j) guardados en memoria.
 * <p>
 * Cada clave (p. ej. "login:203.0.113.7") tiene su propio bucket. Con varias instancias del backend
 * cada una llevaría su cuenta; haría falta un almacén compartido (Redis...).
 */
@Component
public class RateLimiter {

    /**
     * @param name     prefijo de la clave, distingue límites distintos para un mismo cliente
     * @param capacity peticiones permitidas en cada periodo
     * @param period   periodo en el que se recupera la capacidad completa
     */
    public record Limit(String name, int capacity, Duration period) {
    }

    // Debe superar el periodo más largo de los límites: si no, un bucket agotado se olvidaría antes de tiempo
    private static final Duration IDLE_EXPIRATION = Duration.ofHours(1);

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(IDLE_EXPIRATION)
            .maximumSize(100_000)
            .build();

    /**
     * Consume una petición.
     *
     * @throws TooManyRequestsException si el cliente ha agotado el límite
     */
    public void consume(Limit limit, String client) {
        ConsumptionProbe probe = bucket(limit, client).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new TooManyRequestsException(toSeconds(probe.getNanosToWaitForRefill()));
        }
    }

    /**
     * Comprueba que queda alguna petición disponible, sin consumirla.
     *
     * @throws TooManyRequestsException si el límite está agotado
     */
    public void requireAvailable(Limit limit, String client) {
        long nanosToWait = bucket(limit, client).estimateAbilityToConsume(1).getNanosToWaitForRefill();
        if (nanosToWait > 0) {
            throw new TooManyRequestsException(toSeconds(nanosToWait));
        }
    }

    /**
     * Resta una petición si queda alguna (para contar intentos fallidos). Nunca lanza excepción.
     */
    public void penalize(Limit limit, String client) {
        bucket(limit, client).tryConsume(1);
    }

    private Bucket bucket(Limit limit, String client) {
        return buckets.get(limit.name() + ":" + client, key -> Bucket.builder()
                .addLimit(bandwidth -> bandwidth.capacity(limit.capacity()).refillGreedy(limit.capacity(), limit.period()))
                .build());
    }

    private long toSeconds(long nanos) {
        return Math.max(1, TimeUnit.NANOSECONDS.toSeconds(nanos) + 1);
    }
}
