package org.iesalixar.daw2.GarikAsatryan.valkyria.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.components.RateLimiter;
import org.iesalixar.daw2.GarikAsatryan.valkyria.components.RateLimiter.Limit;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;

/**
 * Límite de peticiones por IP en los endpoints expuestos a abuso (fuerza bruta, spam de emails).
 * <p>
 * Se ejecuta antes de leer y validar el cuerpo, así que las peticiones inválidas también cuentan.
 * La IP es request.getRemoteAddr(), que con server.forward-headers-strategy=framework sale de X-Forwarded-For.
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    // Rutas afectadas; WebConfig registra el interceptor solo en ellas
    static final String[] PATHS = {"/api/v1/auth/**", "/api/v1/users/me/email"};

    private static final Map<String, Limit> LIMITS_BY_ENDPOINT = Map.of(
            "POST /api/v1/auth/login", new Limit("login", 10, Duration.ofMinutes(1)),
            "POST /api/v1/auth/register", new Limit("register", 5, Duration.ofMinutes(10)),
            "POST /api/v1/auth/resend-activation", new Limit("resend-activation", 3, Duration.ofMinutes(10)),
            "POST /api/v1/auth/oauth2/token", new Limit("oauth2-token", 10, Duration.ofMinutes(1)),
            "POST /api/v1/users/me/email", new Limit("email-change", 3, Duration.ofMinutes(10))
    );

    private final RateLimiter rateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Limit limit = LIMITS_BY_ENDPOINT.get(request.getMethod() + " " + request.getRequestURI());
        if (limit != null) {
            rateLimiter.consume(limit, request.getRemoteAddr());
        }
        return true;
    }
}
