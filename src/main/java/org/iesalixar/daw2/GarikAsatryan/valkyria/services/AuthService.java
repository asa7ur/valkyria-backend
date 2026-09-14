package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.config.OAuth2LoginCodeStore;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.AuthResponseDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Emisión de sesiones (JWT + datos del usuario) para el login con contraseña, el login con Google
 * y la renovación del token tras cambiar la contraseña.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final CustomUserDetailsService userDetailsService;
    private final UserService userService;
    private final OAuth2LoginCodeStore oAuth2LoginCodeStore;
    private final JwtUtil jwtUtil;

    @Value("${app.url}")
    private String appUrl;

    public AuthResponseDTO buildAuthResponse(String email) {
        return buildAuthResponse(userDetailsService.loadUserByUsername(email));
    }

    // Recibe siempre un UserDetails recién cargado: el del Authentication ya no tiene la contraseña
    private AuthResponseDTO buildAuthResponse(UserDetails userDetails) {
        String firstName = userService.getUserByEmailEntity(userDetails.getUsername()).getFirstName();

        boolean isAdminOrManager = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MANAGER"));
        String redirectUrl = isAdminOrManager ? appUrl + "/admin/dashboard" : appUrl + "/";

        return new AuthResponseDTO(
                jwtUtil.generateToken(userDetails),
                "Login successful",
                userDetails.getUsername(),
                firstName,
                userDetails.getAuthorities(),
                redirectUrl
        );
    }

    /**
     * Canjea el código de un solo uso del login con Google por una sesión.
     *
     * @throws AppException 401 si el código no existe, ha caducado o ya se usó
     */
    public AuthResponseDTO exchangeOAuth2Code(String code) {
        String email = oAuth2LoginCodeStore.consume(code)
                .orElseThrow(() -> AppException.unauthorized("msg.auth.invalid-oauth-code"));

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        if (!userDetails.isEnabled()) {
            throw new DisabledException("Account disabled");
        }
        return buildAuthResponse(userDetails);
    }
}
