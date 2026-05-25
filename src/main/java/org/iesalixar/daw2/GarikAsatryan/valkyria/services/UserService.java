package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.components.PaginationComponent;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.FilterDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.PasswordChangeDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.ProfileUpdateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.UserDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.UserRegistrationDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.UserUpdateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Role;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.User;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.VerificationToken;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.UserMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.RoleRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PaginationComponent paginationComponent;
    private final EmailService emailService;
    private final VerificationTokenService verificationTokenService;

    /**
     * Obtiene una lista paginada de usuarios usando FilterDTO.
     *
     * @param filterDTO DTO con criterios de búsqueda y paginación.
     * @return Lista de usuarios mapeados a DTO.
     */
    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers(FilterDTO filterDTO) {
        logger.info("Buscando usuarios. Filtro: '{}', Página: {}",
                filterDTO.getSearch() != null ? filterDTO.getSearch().replaceAll("[\r\n]", "_") : null,
                filterDTO.getPage());

        // Creamos el objeto Pageable usando el componente común
        Pageable pageable = paginationComponent.createPageable(filterDTO, "id");

        // Realizamos la búsqueda (con o sin término)
        Page<User> userPage = (filterDTO.getSearch() != null && !filterDTO.getSearch().isBlank())
                ? userRepository.searchUsers(filterDTO.getSearch(), pageable)
                : userRepository.findAll(pageable);

        // Actualizamos los metadatos del filtro (total de páginas, etc.)
        paginationComponent.updateFilterMetadata(filterDTO, userPage);
        return userPage.getContent().stream()
                .map(userMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        return userRepository.findById(id)
                .map(userMapper::toDTO)
                .orElseThrow(() -> new AppException("msg.error.user-not-found", id));
    }

    /**
     * Busca un usuario por email y devuelve la ENTIDAD.
     * Útil para procesos internos como la creación de pedidos.
     */
    public User getUserByEmailEntity(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException("msg.error.user-not-found", email));
    }

    /**
     * Devuelve el perfil del usuario autenticado como DTO.
     */
    @Transactional(readOnly = true)
    public UserDTO getMe(String email) {
        return userRepository.findByEmail(email)
                .map(userMapper::toDTO)
                .orElseThrow(() -> new AppException("msg.error.user-not-found", email));
    }

    /**
     * Actualiza los datos personales del usuario autenticado.
     * No permite cambiar email, roles ni estado enabled.
     */
    @Transactional
    public UserDTO updateMe(String email, ProfileUpdateDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException("msg.error.user-not-found", email));

        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setPhone(dto.getPhone());
        user.setBirthDate(dto.getBirthDate());

        User updated = userRepository.save(user);
        logger.info("Perfil actualizado para el usuario: {}", email.replaceAll("[\r\n]", "_"));
        return userMapper.toDTO(updated);
    }

    /**
     * Cambia la contraseña del usuario autenticado usando su email.
     */
    @Transactional
    public void changePasswordByEmail(String email, PasswordChangeDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException("msg.error.user-not-found", email));

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new AppException("msg.error.invalid-current-password");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
        logger.info("Contraseña actualizada para el usuario: {}", email.replaceAll("[\r\n]", "_"));
    }

    /**
     * CREATE (Admin): Crea un usuario directamente desde la administración.
     * A diferencia del registro público, aquí el admin podría marcarlo como enabled.
     */
    @Transactional
    public UserDTO createUser(UserRegistrationDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new AppException("msg.register.error.email-exists", dto.getEmail());
        }

        User user = userMapper.toEntity(dto);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEnabled(true); // El admin crea usuarios ya activos

        // Asignar USER por defecto o según lógica de admin
        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new AppException("msg.error.role-not-found", "USER"));
        user.setRoles(List.of(userRole));

        return userMapper.toDTO(userRepository.save(user));
    }

    /**
     * UPDATE: Actualiza un usuario existente.
     * No solemos actualizar la contraseña aquí (se hace en un flujo de "cambiar password").
     */
    @Transactional
    public UserDTO updateUser(Long id, UserUpdateDTO dto) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new AppException("msg.error.user-not-found", id));

        if (!existingUser.getEmail().equals(dto.getEmail()) && userRepository.existsByEmail(dto.getEmail())) {
            throw new AppException("msg.register.error.email-exists", dto.getEmail());
        }

        userMapper.updateEntityFromDTO(dto, existingUser);
        existingUser.setEnabled(dto.isEnabled());

        if (dto.getRoles() != null) {
            List<Role> roles = dto.getRoles().stream()
                    .map(roleName -> roleRepository.findByName(roleName)
                            .orElseThrow(() -> new AppException("msg.error.role-not-found", roleName)))
                    .collect(Collectors.toList());
            existingUser.setRoles(roles);
        }

        User updated = userRepository.save(existingUser);
        logger.info("Usuario con ID {} actualizado (Email: {}, Enabled: {}, Roles: {})",
                id, updated.getEmail().replaceAll("[\r\n]", "_"), updated.isEnabled(), dto.getRoles());
        return userMapper.toDTO(updated);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new AppException("msg.error.user-not-found", id);
        }
        userRepository.deleteById(id);
    }

    @Transactional
    public void requestEmailChange(String currentEmail, String newEmail) {
        if (userRepository.existsByEmail(newEmail)) {
            throw new AppException("msg.register.error.email-exists", newEmail);
        }
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new AppException("msg.error.user-not-found", currentEmail));

        String token = verificationTokenService.createEmailChangeToken(user, newEmail);
        emailService.sendEmailChangeEmail(newEmail, user.getFirstName(), token);
        logger.info("Solicitud de cambio de email enviada para: {}", currentEmail.replaceAll("[\r\n]", "_"));
    }

    @Transactional
    public void confirmEmailChange(String token) {
        VerificationToken vToken = verificationTokenService.getVerificationToken(token)
                .filter(t -> t.getPendingEmail() != null)
                .orElseThrow(() -> new AppException("msg.email.change.invalid-token"));

        if (vToken.isExpired()) {
            throw new AppException("msg.email.change.invalid-token");
        }

        User user = vToken.getUser();
        user.setEmail(vToken.getPendingEmail());
        userRepository.save(user);
        verificationTokenService.deleteToken(vToken);
        logger.info("Email actualizado correctamente para usuario con ID {}", user.getId());
    }

    // Este método lo mantenemos solo para el proceso de confirmación de token
    @Transactional
    public void saveUser(User user) {
        userRepository.save(user);
    }

    /**
     * Cambia la contraseña de un usuario tras verificar la contraseña actual.
     */
    @Transactional
    public void changePassword(Long id, PasswordChangeDTO dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException("msg.error.user-not-found", id));

        // 1. Verificar que la contraseña actual es correcta
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new AppException("msg.error.invalid-current-password");
        }

        // 2. Cifrar y guardar la nueva contraseña
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);

        logger.info("Contraseña actualizada para el usuario con ID {}", id);
    }
}