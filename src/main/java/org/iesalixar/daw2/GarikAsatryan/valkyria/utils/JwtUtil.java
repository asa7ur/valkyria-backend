package org.iesalixar.daw2.GarikAsatryan.valkyria.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwtUtil {

    // Huella del hash de la contraseña: al cambiar la contraseña, los tokens anteriores dejan de ser válidos
    static final String PASSWORD_FINGERPRINT_CLAIM = "pwd";

    private final KeyPair jwtKeyPair;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("roles", userDetails.getAuthorities());
        extraClaims.put(PASSWORD_FINGERPRINT_CLAIM, passwordFingerprint(userDetails.getPassword()));
        return generateToken(extraClaims, userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(jwtKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Verifica la firma y la caducidad del token y devuelve sus claims.
     *
     * @throws io.jsonwebtoken.JwtException si la firma no es válida, el token está mal formado o ha caducado
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                // IMPORTANTE: Verificamos con la Clave PÚBLICA
                .verifyWith(jwtKeyPair.getPublic())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Comprueba que unos claims ya verificados siguen correspondiendo al usuario actual:
     * mismo email, cuenta activa y misma contraseña que cuando se emitió el token.
     */
    public boolean isTokenValid(Claims claims, UserDetails userDetails) {
        return userDetails.getUsername().equals(claims.getSubject())
                && userDetails.isEnabled()
                && passwordFingerprint(userDetails.getPassword())
                        .equals(claims.get(PASSWORD_FINGERPRINT_CLAIM, String.class));
    }

    // 96 bits de SHA-256 del hash BCrypt: identifica la contraseña vigente sin exponer su hash en el token
    private String passwordFingerprint(String passwordHash) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((passwordHash != null ? passwordHash : "").getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
