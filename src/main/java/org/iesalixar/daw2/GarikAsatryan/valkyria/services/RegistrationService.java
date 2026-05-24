package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.UserRegistrationDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Role;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.User;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.UserMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.RoleRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

/**
 * Servicio de registro de usuarios con verificación por email.
 * Gestiona el proceso completo de alta de nuevos usuarios en el sistema.
 * <p>
 * Flujo de registro:
 * 1. Validación de email único (no duplicados)
 * 2. Creación de usuario con contraseña cifrada
 * 3. Asignación automática del rol "USER"
 * 4. Usuario creado en estado deshabilitado (enabled=false)
 * 5. Generación de token de verificación temporal
 * 6. Envío de email con enlace de activación
 * 7. Usuario confirma email → cuenta se activa
 * <p>
 * SEGURIDAD:
 * - Contraseñas cifradas con BCrypt (nunca en texto plano)
 * - Tokens de verificación con expiración temporal
 * - Validación de email único para evitar duplicados
 * - Cuentas deshabilitadas hasta confirmación de email
 * <p>
 * Este servicio es transaccional para garantizar atomicidad:
 * si falla cualquier paso, se revierte toda la operación.
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {
    private static final Logger logger = LoggerFactory.getLogger(RegistrationService.class);

    // Inyección de dependencias mediante constructor (Lombok @RequiredArgsConstructor)
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder; // BCrypt
    private final VerificationTokenService verificationTokenService;
    private final EmailService emailService;

    /**
     * Registra un nuevo usuario en el sistema con verificación de email.
     * El usuario queda en estado deshabilitado hasta que confirme su email.
     * <p>
     * Proceso completo:
     * 1. Valida que el email no esté registrado
     * 2. Mapea el DTO a entidad User
     * 3. Cifra la contraseña con BCrypt
     * 4. Asigna el rol USER por defecto
     * 5. Marca la cuenta como deshabilitada (enabled=false)
     * 6. Persiste el usuario en base de datos
     * 7. Genera token de verificación temporal
     * 8. Envía email con enlace de activación
     * <p>
     * TRANSACCIONAL: Si falla el envío de email u otro paso,
     * se revierte la creación del usuario (rollback).
     *
     * @param registrationDTO DTO con datos del registro (email, contraseña, nombre, etc.)
     * @throws AppException si el email ya existe o no se encuentra el rol USER
     */
    @Transactional
    public void registerUser(UserRegistrationDTO registrationDTO) {
        logger.info("Iniciando proceso de registro para usuario: {}", registrationDTO.getEmail().replaceAll("[\r\n]", "_"));
        logger.debug("Datos de registro: Nombre={} {}, Email={}",
                registrationDTO.getFirstName().replaceAll("[\r\n]", "_"),
                registrationDTO.getLastName().replaceAll("[\r\n]", "_"),
                registrationDTO.getEmail().replaceAll("[\r\n]", "_"));

        // ==================== PASO 1: VALIDACIÓN DE EMAIL ÚNICO ====================

        logger.debug("Verificando si el email ya existe en el sistema...");
        if (userRepository.existsByEmail(registrationDTO.getEmail())) {
            logger.warn("Intento de registro con email duplicado: {}", registrationDTO.getEmail().replaceAll("[\r\n]", "_"));
            throw new AppException("msg.register.error.email-exists", registrationDTO.getEmail());
        }
        logger.debug("✓ Email disponible, no existe en el sistema");

        // ==================== PASO 2: MAPEO DTO → ENTIDAD ====================

        logger.debug("Mapeando DTO a entidad User...");
        User user = userMapper.toEntity(registrationDTO);
        logger.trace("Usuario mapeado: {}", user.getEmail());

        // ==================== PASO 3: CIFRADO DE CONTRASEÑA ====================

        // CRÍTICO: NUNCA almacenar contraseñas en texto plano
        logger.debug("Cifrando contraseña con BCrypt...");
        String encodedPassword = passwordEncoder.encode(registrationDTO.getPassword());
        user.setPassword(encodedPassword);
        logger.trace("Contraseña cifrada exitosamente (hash BCrypt generado)");

        // IMPORTANTE: No logueamos la contraseña ni el hash por seguridad

        // ==================== PASO 4: ASIGNACIÓN DE ROL POR DEFECTO ====================

        logger.debug("Buscando rol 'USER' para asignación por defecto...");
        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> {
                    logger.error("CRÍTICO: Rol 'USER' no encontrado en la base de datos. " +
                            "Esto debería haberse creado en los datos iniciales del sistema.");
                    return new AppException("msg.error.role-not-found", "USER");
                });

        user.setRoles(Collections.singletonList(userRole));
        logger.debug("✓ Rol 'USER' asignado al nuevo usuario");

        // ==================== PASO 5: DESHABILITAR CUENTA HASTA VERIFICACIÓN ====================

        // El usuario NO puede iniciar sesión hasta que confirme su email
        user.setEnabled(false);
        logger.debug("Usuario marcado como deshabilitado (enabled=false) hasta verificación de email");

        // ==================== PASO 6: PERSISTENCIA EN BASE DE DATOS ====================

        logger.debug("Guardando usuario en la base de datos...");
        User savedUser = userRepository.save(user);
        logger.info("✓ Usuario guardado en BD. ID: {}, Email: {}",
                savedUser.getId(), savedUser.getEmail().replaceAll("[\r\n]", "_"));

        // ==================== PASO 7: GENERACIÓN DE TOKEN DE VERIFICACIÓN ====================

        logger.debug("Generando token de verificación para el usuario...");
        String token = verificationTokenService.createVerificationToken(savedUser);
        logger.debug("✓ Token de verificación generado (válido por tiempo limitado)");
        logger.trace("Token generado: {}", token); // Solo en TRACE para evitar exposición en logs normales

        // ==================== PASO 8: ENVÍO DE EMAIL DE CONFIRMACIÓN ====================

        logger.info("Enviando email de confirmación a: {}", savedUser.getEmail().replaceAll("[\r\n]", "_"));
        try {
            emailService.sendRegistrationConfirmationEmail(
                    savedUser.getEmail(),
                    savedUser.getFirstName(),
                    token
            );
            logger.info("✓ Email de confirmación enviado exitosamente a {}", savedUser.getEmail().replaceAll("[\r\n]", "_"));

        } catch (Exception e) {
            // Si falla el envío de email, la transacción se revertirá
            logger.error("ERROR al enviar email de confirmación a {}. " +
                            "La transacción será revertida y el usuario NO será creado. Error: {}",
                    savedUser.getEmail().replaceAll("[\r\n]", "_"), e.getMessage(), e);
            throw e; // Re-lanzar para provocar rollback
        }

        logger.info("✓✓ REGISTRO COMPLETADO exitosamente para {}. " +
                        "Usuario debe verificar su email para activar la cuenta.",
                savedUser.getEmail().replaceAll("[\r\n]", "_"));
    }
}