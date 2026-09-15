package org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions;

import lombok.Getter;

/**
 * Se ha superado un límite de peticiones. GlobalExceptionHandler responde 429 con la cabecera Retry-After.
 */
@Getter
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super("Too many requests");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
