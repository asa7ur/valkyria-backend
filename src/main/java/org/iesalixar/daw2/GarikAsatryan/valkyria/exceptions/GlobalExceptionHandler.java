package org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.ResponseDTO;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

// Los códigos de las excepciones estándar de Spring MVC los decide ResponseEntityExceptionHandler; aquí solo se unifica el formato.
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ResponseDTO<Void>> handleAppException(AppException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("Error interno de la aplicación: {}", ex.getMessageKey(), ex);
        }
        return error(ex.getStatus(), ex.getMessageKey(), ex.getArgs());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseDTO<Void>> handleAccessDenied(AccessDeniedException ex) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || new AuthenticationTrustResolverImpl().isAnonymous(authentication)) {
            return error(HttpStatus.UNAUTHORIZED, "msg.error.unauthenticated");
        }
        log.warn("Acceso denegado a {}: {}", authentication.getName(), ex.getMessage());
        return error(HttpStatus.FORBIDDEN, "msg.error.forbidden");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ResponseDTO<Void>> handleBadCredentials() {
        return error(HttpStatus.UNAUTHORIZED, "msg.auth.bad-credentials");
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ResponseDTO<Void>> handleDisabledAccount() {
        return error(HttpStatus.FORBIDDEN, "msg.auth.account-disabled");
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ResponseDTO<Void>> handleTooManyRequests(TooManyRequestsException ex) {
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        return ResponseEntity.status(status)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ResponseDTO.error(status.value(), getMessage("msg.error.too-many-requests", null)));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResponseDTO<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad de datos: {}", ex.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, "msg.error.conflict");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseDTO<Void>> handleGeneralException(Exception ex) {
        log.error("Unhandled exception occurred: ", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "msg.error.internal-server");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing
                ));
        ResponseDTO<Map<String, String>> body =
                ResponseDTO.error(status.value(), getMessage("msg.validation.error", null), errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest
                && servletRequest.getResponse() != null
                && servletRequest.getResponse().isCommitted()) {
            return null;
        }

        if (statusCode.is5xxServerError()) {
            log.error("Error del servidor procesando la petición: ", ex);
        } else {
            log.debug("Petición rechazada ({}): {}", statusCode.value(), ex.getMessage());
        }

        Object responseBody = body instanceof ResponseDTO<?>
                ? body
                : ResponseDTO.error(statusCode.value(), getMessage(messageKeyFor(statusCode), null));
        return new ResponseEntity<>(responseBody, headers, statusCode);
    }

    private String messageKeyFor(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 404 -> "msg.error.not-found";
            case 405 -> "msg.error.method-not-allowed";
            case 413 -> "msg.error.payload-too-large";
            case 415 -> "msg.error.unsupported-media-type";
            default -> statusCode.is4xxClientError() ? "msg.error.bad-request" : "msg.error.internal-server";
        };
    }

    private ResponseEntity<ResponseDTO<Void>> error(HttpStatus status, String messageKey, Object... args) {
        return ResponseEntity.status(status).body(ResponseDTO.error(status.value(), getMessage(messageKey, args)));
    }

    private String getMessage(String key, Object[] args) {
        // Los números se pasan como texto para que MessageFormat no los formatee como "999.999"
        Object[] formattedArgs = args == null ? null : Arrays.stream(args)
                .map(arg -> arg instanceof Number ? String.valueOf(arg) : arg)
                .toArray();
        return messageSource.getMessage(key, formattedArgs, LocaleContextHolder.getLocale());
    }
}
