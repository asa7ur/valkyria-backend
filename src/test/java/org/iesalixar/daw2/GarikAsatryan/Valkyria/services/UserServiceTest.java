package org.iesalixar.daw2.GarikAsatryan.Valkyria.services;

import org.iesalixar.daw2.GarikAsatryan.valkyria.components.PaginationComponent;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.*;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Role;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.User;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.VerificationToken;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.UserMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.RoleRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.UserRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.EmailService;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.UserService;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.VerificationTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PaginationComponent paginationComponent;
    @Mock private EmailService emailService;
    @Mock private VerificationTokenService verificationTokenService;

    @InjectMocks
    private UserService userService;

    // ─── helpers ───────────────────────────────────────────────────────────────

    private User makeUser(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setPassword("$2a$encoded");
        u.setFirstName("Test");
        u.setLastName("User");
        return u;
    }

    private Role makeRole(String name) {
        Role r = new Role();
        r.setId(1L);
        r.setName(name);
        return r;
    }

    // ─── getAllUsers ──────────────────────────────────────────────────────────

    @Test
    void getAllUsers_withoutSearch_callsFindAll() {
        FilterDTO filter = new FilterDTO();
        filter.setPage(0);
        filter.setItemsPerPage(10);
        Pageable pageable = PageRequest.of(0, 10);
        User user = makeUser(1L, "user@test.com");
        Page<User> page = new PageImpl<>(List.of(user));
        UserDTO dto = new UserDTO();

        when(paginationComponent.createPageable(filter, "id")).thenReturn(pageable);
        when(userRepository.findAll(pageable)).thenReturn(page);
        when(userMapper.toDTO(user)).thenReturn(dto);

        List<UserDTO> result = userService.getAllUsers(filter);

        assertThat(result).hasSize(1);
        verify(userRepository).findAll(pageable);
        verify(userRepository, never()).searchUsers(any(), any());
    }

    @Test
    void getAllUsers_withSearch_callsSearchUsers() {
        FilterDTO filter = new FilterDTO();
        filter.setPage(0);
        filter.setItemsPerPage(10);
        filter.setSearch("Ana");
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of());

        when(paginationComponent.createPageable(filter, "id")).thenReturn(pageable);
        when(userRepository.searchUsers("Ana", pageable)).thenReturn(page);

        List<UserDTO> result = userService.getAllUsers(filter);

        assertThat(result).isEmpty();
        verify(userRepository).searchUsers("Ana", pageable);
        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    // ─── getUserById ─────────────────────────────────────────────────────────

    @Test
    void getUserById_found_returnsDTO() {
        User user = makeUser(1L, "user@test.com");
        UserDTO dto = new UserDTO();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toDTO(user)).thenReturn(dto);

        assertThat(userService.getUserById(1L)).isEqualTo(dto);
    }

    @Test
    void getUserById_notFound_throwsAppException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.user-not-found");
    }

    // ─── getUserByEmailEntity ─────────────────────────────────────────────────

    @Test
    void getUserByEmailEntity_found_returnsUser() {
        User user = makeUser(1L, "user@test.com");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        assertThat(userService.getUserByEmailEntity("user@test.com")).isEqualTo(user);
    }

    @Test
    void getUserByEmailEntity_notFound_throwsAppException() {
        when(userRepository.findByEmail("none@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserByEmailEntity("none@test.com"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.user-not-found");
    }

    // ─── getMe ───────────────────────────────────────────────────────────────

    @Test
    void getMe_found_returnsDTO() {
        User user = makeUser(1L, "user@test.com");
        UserDTO dto = new UserDTO();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(userMapper.toDTO(user)).thenReturn(dto);

        assertThat(userService.getMe("user@test.com")).isEqualTo(dto);
    }

    @Test
    void getMe_notFound_throwsAppException() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMe("ghost@test.com"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.user-not-found");
    }

    // ─── updateMe ────────────────────────────────────────────────────────────

    @Test
    void updateMe_validData_updatesProfileFields() {
        User user = makeUser(1L, "user@test.com");
        ProfileUpdateDTO dto = new ProfileUpdateDTO();
        dto.setFirstName("Nuevo");
        dto.setLastName("Apellido");
        dto.setPhone("699999999");
        dto.setBirthDate(LocalDate.of(1990, 1, 1));
        UserDTO responseDTO = new UserDTO();

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toDTO(user)).thenReturn(responseDTO);

        UserDTO result = userService.updateMe("user@test.com", dto);

        assertThat(result).isEqualTo(responseDTO);
        assertThat(user.getFirstName()).isEqualTo("Nuevo");
        assertThat(user.getLastName()).isEqualTo("Apellido");
        assertThat(user.getPhone()).isEqualTo("699999999");
        verify(userRepository).save(user);
    }

    @Test
    void updateMe_userNotFound_throwsAppException() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateMe("ghost@test.com", new ProfileUpdateDTO()))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.user-not-found");
    }

    // ─── changePasswordByEmail ────────────────────────────────────────────────

    @Test
    void changePasswordByEmail_correctCurrentPassword_updatesPassword() {
        User user = makeUser(1L, "user@test.com");
        PasswordChangeDTO dto = new PasswordChangeDTO();
        dto.setCurrentPassword("OldPass1@");
        dto.setNewPassword("NewPass2@");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass1@", "$2a$encoded")).thenReturn(true);
        when(passwordEncoder.encode("NewPass2@")).thenReturn("$2a$new_hash");

        assertThatNoException().isThrownBy(() -> userService.changePasswordByEmail("user@test.com", dto));

        assertThat(user.getPassword()).isEqualTo("$2a$new_hash");
        verify(userRepository).save(user);
    }

    @Test
    void changePasswordByEmail_wrongCurrentPassword_throwsAppException() {
        User user = makeUser(1L, "user@test.com");
        PasswordChangeDTO dto = new PasswordChangeDTO();
        dto.setCurrentPassword("WrongPass1@");
        dto.setNewPassword("NewPass2@");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass1@", "$2a$encoded")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePasswordByEmail("user@test.com", dto))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.invalid-current-password");

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordByEmail_userNotFound_throwsAppException() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePasswordByEmail("ghost@test.com", new PasswordChangeDTO()))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.user-not-found");
    }

    // ─── createUser (admin) ───────────────────────────────────────────────────

    @Test
    void createUser_validData_createsEnabledUser() {
        UserRegistrationDTO dto = new UserRegistrationDTO();
        dto.setEmail("admin-created@test.com");
        dto.setPassword("Secure1@");
        User user = new User();
        UserDTO responseDTO = new UserDTO();

        when(userRepository.existsByEmail("admin-created@test.com")).thenReturn(false);
        when(userMapper.toEntity(dto)).thenReturn(user);
        when(passwordEncoder.encode("Secure1@")).thenReturn("$2a$hash");
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(makeRole("USER")));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toDTO(user)).thenReturn(responseDTO);

        UserDTO result = userService.createUser(dto);

        assertThat(result).isEqualTo(responseDTO);
        assertThat(user.isEnabled()).isTrue(); // admin-created users are active immediately
        verify(userRepository).save(user);
    }

    @Test
    void createUser_duplicateEmail_throwsAppException() {
        UserRegistrationDTO dto = new UserRegistrationDTO();
        dto.setEmail("existing@test.com");
        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(dto))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.register.error.email-exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_roleNotFound_throwsAppException() {
        UserRegistrationDTO dto = new UserRegistrationDTO();
        dto.setEmail("new@test.com");
        dto.setPassword("Pass1@");
        User user = new User();

        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(userMapper.toEntity(dto)).thenReturn(user);
        when(passwordEncoder.encode(any())).thenReturn("$2a$hash");
        when(roleRepository.findByName("USER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.createUser(dto))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.role-not-found");
    }

    // ─── updateUser ───────────────────────────────────────────────────────────

    @Test
    void updateUser_notFound_throwsAppException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(99L, new UserUpdateDTO()))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.user-not-found");
    }

    @Test
    void updateUser_changingToAlreadyExistingEmail_throwsAppException() {
        User existing = makeUser(1L, "original@test.com");
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setEmail("taken@test.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateUser(1L, dto))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.register.error.email-exists");
    }

    @Test
    void updateUser_keepingSameEmail_doesNotCheckDuplicate() {
        User existing = makeUser(1L, "same@test.com");
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setEmail("same@test.com"); // same email, no duplicate check needed

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        when(userMapper.toDTO(existing)).thenReturn(new UserDTO());

        assertThatNoException().isThrownBy(() -> userService.updateUser(1L, dto));

        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void updateUser_withRoles_assignsRolesToUser() {
        User existing = makeUser(1L, "user@test.com");
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setEmail("user@test.com");
        dto.setEnabled(true);
        dto.setRoles(List.of("ADMIN"));
        Role adminRole = makeRole("ADMIN");

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.save(existing)).thenReturn(existing);
        when(userMapper.toDTO(existing)).thenReturn(new UserDTO());

        userService.updateUser(1L, dto);

        assertThat(existing.getRoles()).containsExactly(adminRole);
    }

    // ─── deleteUser ───────────────────────────────────────────────────────────

    @Test
    void deleteUser_exists_deletesSuccessfully() {
        when(userRepository.existsById(1L)).thenReturn(true);

        assertThatNoException().isThrownBy(() -> userService.deleteUser(1L));
        verify(userRepository).deleteById(1L);
    }

    @Test
    void deleteUser_notFound_throwsAppException() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteUser(99L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.user-not-found");

        verify(userRepository, never()).deleteById(any());
    }

    // ─── requestEmailChange ───────────────────────────────────────────────────

    @Test
    void requestEmailChange_newEmailAlreadyInUse_throwsAppException() {
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.requestEmailChange("current@test.com", "taken@test.com"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.register.error.email-exists");

        verify(emailService, never()).sendEmailChangeEmail(any(), any(), any());
    }

    @Test
    void requestEmailChange_valid_generatesTokenAndSendsEmail() {
        User user = makeUser(1L, "current@test.com");
        user.setFirstName("Carlos");

        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(userRepository.findByEmail("current@test.com")).thenReturn(Optional.of(user));
        when(verificationTokenService.createEmailChangeToken(user, "new@test.com")).thenReturn("change-token");

        assertThatNoException().isThrownBy(() -> userService.requestEmailChange("current@test.com", "new@test.com"));

        verify(verificationTokenService).createEmailChangeToken(user, "new@test.com");
        verify(emailService).sendEmailChangeEmail("new@test.com", "Carlos", "change-token");
    }

    // ─── confirmEmailChange ───────────────────────────────────────────────────

    @Test
    void confirmEmailChange_validToken_updatesUserEmail() {
        User user = makeUser(1L, "old@test.com");
        VerificationToken token = new VerificationToken("valid-token", user, "new@test.com");
        token.setExpiryDate(LocalDateTime.now().plusHours(1));

        when(verificationTokenService.getVerificationToken("valid-token")).thenReturn(Optional.of(token));
        when(userRepository.save(user)).thenReturn(user);

        assertThatNoException().isThrownBy(() -> userService.confirmEmailChange("valid-token"));

        assertThat(user.getEmail()).isEqualTo("new@test.com");
        verify(userRepository).save(user);
        verify(verificationTokenService).deleteToken(token);
    }

    @Test
    void confirmEmailChange_expiredToken_throwsAppException() {
        User user = makeUser(1L, "old@test.com");
        VerificationToken token = new VerificationToken("expired-token", user, "new@test.com");
        token.setExpiryDate(LocalDateTime.now().minusHours(1)); // already expired

        when(verificationTokenService.getVerificationToken("expired-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> userService.confirmEmailChange("expired-token"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.email.change.invalid-token");

        verify(userRepository, never()).save(any());
        verify(verificationTokenService, never()).deleteToken(any());
    }

    @Test
    void confirmEmailChange_tokenNotFound_throwsAppException() {
        when(verificationTokenService.getVerificationToken("unknown-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.confirmEmailChange("unknown-token"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.email.change.invalid-token");
    }

    @Test
    void confirmEmailChange_tokenWithoutPendingEmail_throwsAppException() {
        // A registration token (no pendingEmail) should not be accepted here
        User user = makeUser(1L, "user@test.com");
        VerificationToken regToken = new VerificationToken("reg-token", user);
        regToken.setExpiryDate(LocalDateTime.now().plusHours(1));

        when(verificationTokenService.getVerificationToken("reg-token"))
                .thenReturn(Optional.of(regToken));

        assertThatThrownBy(() -> userService.confirmEmailChange("reg-token"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.email.change.invalid-token");
    }
}
