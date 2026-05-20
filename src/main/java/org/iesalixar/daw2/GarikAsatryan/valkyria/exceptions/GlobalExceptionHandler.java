package org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.ResponseDTO;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.nio.file.AccessDeniedException;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    /**
     * Maneja las excepciones personalizadas de la aplicación usando i18n.
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ResponseDTO<Void>> handleAppException(AppException ex) {
        String errorMessage = getMessage(ex.getMessageKey(), ex.getArgs());
        return ResponseEntity
                .status(ex.getStatus())
                .body(ResponseDTO.error(ex.getStatus().value(), errorMessage));
    }

    /**
     * Maneja excepciones de denegación de acceso (Spring Security).
     * Esto evita que caigan en el handleGeneralException (500).
     */
    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ResponseDTO<Void>> handleAccessDeniedException(Exception ex) {
        log.warn("Acceso denegado: {}", ex.getMessage());
        String message = "Sesión caducada o permisos insuficientes";
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ResponseDTO.error(HttpStatus.FORBIDDEN.value(), message));
    }

    /**
     * Maneja errores de validación (@Valid) traduciendo el mensaje principal.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseDTO<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing
                ));
        String message = getMessage("msg.validation.error", null);
        return ResponseEntity.badRequest()
                .body(ResponseDTO.error(HttpStatus.BAD_REQUEST.value(), message, errors));
    }

    /**
     * Maneja errores 404 de recursos no encontrados de Spring.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ResponseDTO<Void>> handleNotFound(NoResourceFoundException ex) {
        String message = getMessage("msg.error.not-found", null);
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ResponseDTO.error(HttpStatus.NOT_FOUND.value(), message));
    }

    /**
     * Maneja cualquier otra excepción no controlada.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseDTO<Void>> handleGeneralException(Exception ex) {
        log.error("Unhandled exception occurred: ", ex);
        String message = getMessage("msg.error.internal-server", null);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseDTO.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), message));
    }

    /**
     * Método privado de utilidad para obtener mensajes traducidos de forma limpia.
     */
    private String getMessage(String key, Object[] args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}