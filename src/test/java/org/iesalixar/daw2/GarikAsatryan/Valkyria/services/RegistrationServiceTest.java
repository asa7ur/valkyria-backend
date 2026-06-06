package org.iesalixar.daw2.GarikAsatryan.Valkyria.services;

import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.UserRegistrationDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Role;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.User;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.UserMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.RoleRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.UserRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.EmailService;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.RegistrationService;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.VerificationTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private VerificationTokenService verificationTokenService;
    @Mock private EmailService emailService;

    @InjectMocks
    private RegistrationService registrationService;

    // ─── helpers ───────────────────────────────────────────────────────────────

    private UserRegistrationDTO makeDTO() {
        UserRegistrationDTO dto = new UserRegistrationDTO();
        dto.setEmail("nuevo@test.com");
        dto.setPassword("Password1@");
        dto.setConfirmPassword("Password1@");
        dto.setFirstName("Ana");
        dto.setLastName("García");
        dto.setBirthDate(LocalDate.of(1995, 3, 15));
        dto.setPhone("600123456");
        return dto;
    }

    private Role userRole() {
        Role role = new Role();
        role.setId(1L);
        role.setName("USER");
        return role;
    }

    private User savedUser(String email, String firstName) {
        User u = new User();
        u.setId(1L);
        u.setEmail(email);
        u.setFirstName(firstName);
        return u;
    }

    // ─── registerUser ────────────────────────────────────────────────────────

    @Test
    void registerUser_validData_savesUserAsDisabledAndSendsEmail() {
        UserRegistrationDTO dto = makeDTO();
        User mappedUser = new User();
        User saved = savedUser("nuevo@test.com", "Ana");

        when(userRepository.existsByEmail("nuevo@test.com")).thenReturn(false);
        when(userMapper.toEntity(dto)).thenReturn(mappedUser);
        when(passwordEncoder.encode("Password1@")).thenReturn("$2a$hash");
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole()));
        when(userRepository.save(mappedUser)).thenReturn(saved);
        when(verificationTokenService.createVerificationToken(saved)).thenReturn("abc-token");

        registrationService.registerUser(dto);

        assertThat(mappedUser.isEnabled()).isFalse();
        assertThat(mappedUser.getPassword()).isEqualTo("$2a$hash");
        verify(userRepository).save(mappedUser);
        verify(verificationTokenService).createVerificationToken(saved);
        verify(emailService).sendRegistrationConfirmationEmail("nuevo@test.com", "Ana", "abc-token");
    }

    @Test
    void registerUser_duplicateEmail_throwsAppException() {
        when(userRepository.existsByEmail("nuevo@test.com")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.registerUser(makeDTO()))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.register.error.email-exists");

        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendRegistrationConfirmationEmail(any(), any(), any());
    }

    @Test
    void registerUser_roleUserNotFound_throwsAppException() {
        UserRegistrationDTO dto = makeDTO();
        when(userRepository.existsByEmail(dto.getEmail())).thenReturn(false);
        when(userMapper.toEntity(dto)).thenReturn(new User());
        when(passwordEncoder.encode(any())).thenReturn("$2a$hash");
        when(roleRepository.findByName("USER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.registerUser(dto))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.role-not-found");

        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendRegistrationConfirmationEmail(any(), any(), any());
    }

    @Test
    void registerUser_emailSendingFails_exceptionPropagates() {
        // If email fails, the exception must propagate to trigger the @Transactional rollback
        UserRegistrationDTO dto = makeDTO();
        User user = new User();
        User saved = savedUser("nuevo@test.com", "Ana");

        when(userRepository.existsByEmail(dto.getEmail())).thenReturn(false);
        when(userMapper.toEntity(dto)).thenReturn(user);
        when(passwordEncoder.encode(any())).thenReturn("$2a$hash");
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole()));
        when(userRepository.save(user)).thenReturn(saved);
        when(verificationTokenService.createVerificationToken(saved)).thenReturn("abc-token");
        doThrow(new RuntimeException("SMTP unreachable")).when(emailService)
                .sendRegistrationConfirmationEmail(any(), any(), any());

        assertThatThrownBy(() -> registrationService.registerUser(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SMTP unreachable");
    }

    @Test
    void registerUser_passwordIsEncodedBeforePersistence() {
        UserRegistrationDTO dto = makeDTO();
        User user = new User();
        User saved = savedUser("nuevo@test.com", "Ana");

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userMapper.toEntity(dto)).thenReturn(user);
        when(passwordEncoder.encode("Password1@")).thenReturn("$2a$bcrypt_hash");
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole()));
        when(userRepository.save(user)).thenReturn(saved);
        when(verificationTokenService.createVerificationToken(saved)).thenReturn("token");

        registrationService.registerUser(dto);

        assertThat(user.getPassword()).isEqualTo("$2a$bcrypt_hash");
        assertThat(user.getPassword()).doesNotContain("Password1@");
    }

    @Test
    void registerUser_assignsRoleUserToNewUser() {
        UserRegistrationDTO dto = makeDTO();
        User user = new User();
        User saved = savedUser("nuevo@test.com", "Ana");
        Role role = userRole();

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userMapper.toEntity(dto)).thenReturn(user);
        when(passwordEncoder.encode(any())).thenReturn("$2a$hash");
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(role));
        when(userRepository.save(user)).thenReturn(saved);
        when(verificationTokenService.createVerificationToken(saved)).thenReturn("token");

        registrationService.registerUser(dto);

        assertThat(user.getRoles()).containsExactly(role);
    }
}
