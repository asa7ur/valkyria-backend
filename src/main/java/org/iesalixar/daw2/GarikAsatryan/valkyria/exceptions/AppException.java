package org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {
    private final String messageKey;
    private final Object[] args;
    private final HttpStatus status;

    private AppException(HttpStatus status, String messageKey, Object... args) {
        super(messageKey);
        this.messageKey = messageKey;
        this.status = status;
        this.args = args;
    }

    public static AppException badRequest(String messageKey, Object... args) {
        return new AppException(HttpStatus.BAD_REQUEST, messageKey, args);
    }

    public static AppException forbidden(String messageKey, Object... args) {
        return new AppException(HttpStatus.FORBIDDEN, messageKey, args);
    }

    public static AppException notFound(String messageKey, Object... args) {
        return new AppException(HttpStatus.NOT_FOUND, messageKey, args);
    }

    public static AppException conflict(String messageKey, Object... args) {
        return new AppException(HttpStatus.CONFLICT, messageKey, args);
    }

    public static AppException internal(String messageKey, Object... args) {
        return new AppException(HttpStatus.INTERNAL_SERVER_ERROR, messageKey, args);
    }
}
