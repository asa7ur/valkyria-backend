package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.User;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.VerificationToken;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.VerificationTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VerificationTokenService {

    private final VerificationTokenRepository tokenRepository;

    /**
     * Crea un nuevo token de verificación para un usuario y lo guarda.
     * Genera una cadena aleatoria única (UUID).
     */
    @Transactional
    public String createVerificationToken(User user) {
        String token = UUID.randomUUID().toString();
        VerificationToken myToken = new VerificationToken(token, user);
        tokenRepository.save(myToken);
        return token;
    }

    /**
     * Recupera un token de la base de datos.
     */
    public Optional<VerificationToken> getVerificationToken(String token) {
        return tokenRepository.findByToken(token);
    }

    /**
     * Elimina el token (se usará una vez que el usuario haya activado su cuenta).
     */
    @Transactional
    public void deleteToken(VerificationToken token) {
        tokenRepository.delete(token);
    }

    /**
     * Crea un token de cambio de email, eliminando previamente cualquier token de cambio pendiente.
     */
    @Transactional
    public String createEmailChangeToken(User user, String pendingEmail) {
        tokenRepository.findByUser(user).stream()
                .filter(t -> t.getPendingEmail() != null)
                .forEach(tokenRepository::delete);

        String token = UUID.randomUUID().toString();
        tokenRepository.save(new VerificationToken(token, user, pendingEmail));
        return token;
    }

    /**
     * Busca todos los tokens de un usuario (útil para limpiezas o reenvíos).
     */
    public List<VerificationToken> getTokensByUser(User user) {
        return tokenRepository.findByUser(user);
    }
}