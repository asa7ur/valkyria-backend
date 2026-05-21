package org.iesalixar.daw2.GarikAsatryan.valkyria.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.AuthRequestDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.AuthResponseDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.UserRegistrationDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.User;
import org.iesalixar.daw2.GarikAsatryan.valkyria.utils.JwtUtil;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.RegistrationService;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.UserService;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.VerificationTokenService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RegistrationService registrationService;
    private final MessageSource messageSource;
    private final VerificationTokenService verificationTokenService;
    private final UserService userService;

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
        try {
            // 1. Autenticar credenciales
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getUsername(), authRequest.getPassword())
            );

            // 2. Cargar datos del usuario y generar Token
            String username = authentication.getName();
            User userEntity = userService.getUserByEmailEntity(username);
            final String jwt = jwtUtil.generateToken((UserDetails) authentication.getPrincipal());

            // 3. Lógica de redirección para el frontend
            boolean isAdminOrManager = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MANAGER"));

            String redirectUrl = isAdminOrManager ? "http://localhost:4200/admin/dashboard" : "http://localhost:4200/";

            // 4. Devolver DTO completo
            return ResponseEntity.ok(new AuthResponseDTO(
                    jwt,
                    "Login successful",
                    username,
                    userEntity.getFirstName(),
                    authentication.getAuthorities(),
                    redirectUrl
            ));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponseDTO(null, "Credenciales inválidas. Por favor, verifica tus datos."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AuthResponseDTO(null, "Ocurrió un error inesperado."));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody UserRegistrationDTO registrationDTO) {
        registrationService.registerUser(registrationDTO);
        Map<String, String> response = new HashMap<>();
        response.put("message", messageSource.getMessage("msg.register.success", null, LocaleContextHolder.getLocale()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/confirm")
    public ResponseEntity<?> confirmRegistration(@RequestParam("token") String token) {
        return verificationTokenService.getVerificationToken(token)
                .map(verificationToken -> {
                    if (verificationToken.isExpired()) {
                        Map<String, String> response = new HashMap<>();
                        response.put("error", messageSource.getMessage("msg.register.error.invalidToken", null, LocaleContextHolder.getLocale()));
                        return ResponseEntity.badRequest().body(response);
                    }

                    User user = verificationToken.getUser();
                    user.setEnabled(true);
                    userService.saveUser(user);
                    verificationTokenService.deleteToken(verificationToken);

                    Map<String, String> response = new HashMap<>();
                    response.put("message", "Account activated");
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    Map<String, String> response = new HashMap<>();
                    response.put("error", "Invalid token");
                    return ResponseEntity.badRequest().body(response);
                });
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<AuthResponseDTO> handleAppException(AppException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new AuthResponseDTO(null, "Error: " + e.getMessageKey()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AuthResponseDTO> handleException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new AuthResponseDTO(null, "Error: " + e.getMessage()));
    }
}