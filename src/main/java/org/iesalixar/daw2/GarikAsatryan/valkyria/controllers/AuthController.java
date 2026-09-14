package org.iesalixar.daw2.GarikAsatryan.valkyria.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.*;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.AuthService;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.RegistrationService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AuthService authService;
    private final RegistrationService registrationService;
    private final MessageSource messageSource;

    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(Authentication authentication) {
        // Si no hay autenticación o es una sesión anónima, el token no es válido/ha expirado
        if (authentication == null ||
                !authentication.isAuthenticated() ||
                authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody AuthRequestDTO authRequest) {
        // BadCredentials/Disabled los traduce GlobalExceptionHandler
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(authRequest.getUsername(), authRequest.getPassword())
        );

        // Se recarga el usuario: Spring borra la contraseña del principal tras autenticar y el JWT necesita su huella
        return ResponseEntity.ok(authService.buildAuthResponse(authentication.getName()));
    }

    /**
     * Segundo paso del login con Google: canjea el código de un solo uso que el backend puso en la redirección.
     */
    @PostMapping("/oauth2/token")
    public ResponseEntity<AuthResponseDTO> exchangeOAuth2Code(@Valid @RequestBody OAuth2CodeExchangeDTO request) {
        return ResponseEntity.ok(authService.exchangeOAuth2Code(request.getCode()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody UserRegistrationDTO registrationDTO) {
        registrationService.registerUser(registrationDTO);
        Map<String, String> response = new HashMap<>();
        response.put("message", getMessage("msg.register.success"));
        return ResponseEntity.ok(response);
    }

    /**
     * Responde siempre lo mismo, exista o no una cuenta pendiente de activar con ese email.
     */
    @PostMapping("/resend-activation")
    public ResponseEntity<ResponseDTO<Void>> resendActivation(@Valid @RequestBody ResendActivationDTO request) {
        registrationService.resendActivation(request.getEmail());
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.register.resend.sent"), null));
    }

    @GetMapping("/confirm")
    public ResponseEntity<ResponseDTO<Void>> confirmRegistration(@RequestParam("token") String token) {
        registrationService.confirmAccount(token);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.register.confirm.success"), null));
    }

    private String getMessage(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }
}
