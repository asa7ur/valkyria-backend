package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.User;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Servicio personalizado para la carga de detalles de usuario en Spring Security.
 * Implementa la interfaz {@link UserDetailsService} para integrar el sistema de autenticación
 * con la entidad User del dominio de la aplicación.
 * <p>
 * Este servicio es invocado automáticamente por Spring Security durante el proceso de autenticación
 * para recuperar la información del usuario y sus roles/permisos.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);

    // Inyección de dependencias mediante constructor (Lombok @RequiredArgsConstructor)
    private final UserRepository userRepository;

    /**
     * Carga los detalles de un usuario por su email (usado como username en este sistema).
     * Este método es invocado automáticamente por Spring Security durante la autenticación.
     * <p>
     * Proceso:
     * 1. Busca el usuario en la base de datos por email
     * 2. Transforma la entidad User del dominio en un UserDetails de Spring Security
     * 3. Mapea los roles del usuario con el prefijo "ROLE_" requerido por Spring Security
     * 4. Configura el estado de la cuenta (habilitada, bloqueada, expirada, etc.)
     *
     * @param email Email del usuario (utilizado como identificador único)
     * @return UserDetails objeto que contiene la información de seguridad del usuario
     * @throws UsernameNotFoundException si no se encuentra ningún usuario con ese email
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        logger.debug("Intentando cargar usuario con email: {}", email.replaceAll("[\r\n]", "_"));

        // Paso 1: Buscar el usuario en la base de datos
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    logger.warn("Intento de autenticación fallido. Usuario no encontrado: {}", email.replaceAll("[\r\n]", "_"));
                    return new UsernameNotFoundException("User not found with email: " + email);
                });

        logger.debug("Usuario encontrado: {}. Estado habilitado: {}",
                user.getEmail(), user.isEnabled());

        // Paso 2: Mapear los roles del usuario al formato esperado por Spring Security
        // Spring Security requiere que los roles tengan el prefijo "ROLE_"
        String[] authorities = user.getRoles().stream()
                .map(role -> {
                    String authority = "ROLE_" + role.getName().toUpperCase();
                    logger.trace("Mapeando rol: {} -> {}", role.getName(), authority);
                    return authority;
                })
                .toList()
                .toArray(new String[0]);

        logger.debug("Roles asignados al usuario {}: {}", email.replaceAll("[\r\n]", "_"), String.join(", ", authorities));

        // Paso 3: Construir el objeto UserDetails de Spring Security
        // Este objeto contiene toda la información necesaria para la autenticación y autorización
        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPassword() != null ? user.getPassword() : "") // Null para usuarios OAuth2
                .authorities(authorities) // Roles con prefijo ROLE_
                .accountExpired(false) // La cuenta no está expirada
                .accountLocked(false) // La cuenta no está bloqueada
                .credentialsExpired(false) // Las credenciales no están expiradas
                .disabled(!user.isEnabled()) // Estado basado en el campo 'enabled' de la entidad User
                .build();
    }
}