package org.iesalixar.daw2.GarikAsatryan.valkyria.security;

import com.jayway.jsonpath.JsonPath;
import jakarta.mail.internet.MimeMessage;
import org.iesalixar.daw2.GarikAsatryan.valkyria.AbstractIntegrationTest;
import org.iesalixar.daw2.GarikAsatryan.valkyria.config.OAuth2AuthenticationSuccessHandler;
import org.iesalixar.daw2.GarikAsatryan.valkyria.config.OAuth2LoginCodeStore;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.User;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.VerificationToken;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.RoleRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.UserRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.CustomOAuth2UserService;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.VerificationTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthHardeningIntegrationTest extends AbstractIntegrationTest {

    private static final String NEW_PASSWORD = "Nueva1234!";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VerificationTokenService verificationTokenService;

    @Autowired
    private OAuth2LoginCodeStore codeStore;

    @Autowired
    private OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.url}")
    private String appUrl;

    @BeforeEach
    void mockMimeMessages() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((jakarta.mail.Session) null));
    }

    // ─── Tokens JWT ────────────────────────────────────────────────────────────

    @Test
    void disabledUser_existingTokenStopsWorking() throws Exception {
        User user = newUser(true);
        String token = login(user.getEmail(), TEST_PASSWORD);
        getMe(token).andExpect(status().isOk());

        user.setEnabled(false);
        userRepository.save(user);

        getMe(token).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChange_invalidatesPreviousTokens_andReturnsNewSession() throws Exception {
        User user = newUser(true);
        String oldToken = login(user.getEmail(), TEST_PASSWORD);

        String body = mockMvc.perform(patch("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChangeJson(TEST_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(user.getEmail()))
                .andReturn().getResponse().getContentAsString();
        String newToken = JsonPath.read(body, "$.data.token");

        getMe(oldToken).andExpect(status().isUnauthorized());
        getMe(newToken).andExpect(status().isOk());
    }

    @Test
    void passwordChange_weakPassword_returns400() throws Exception {
        User user = newUser(true);

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + login(user.getEmail(), TEST_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChangeJson(TEST_PASSWORD, "abcdefgh")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.newPassword").exists());
    }

    @Test
    void adminPasswordReset_doesNotRequireCurrentPassword_andInvalidatesUserTokens() throws Exception {
        User user = newUser(true);
        String userToken = login(user.getEmail(), TEST_PASSWORD);

        mockMvc.perform(patch("/api/v1/users/" + user.getId() + "/password")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}".formatted(NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk());

        getMe(userToken).andExpect(status().isUnauthorized());
        login(user.getEmail(), NEW_PASSWORD);
    }

    // ─── Login con Google ──────────────────────────────────────────────────────

    @Test
    void oauth2Code_canOnlyBeExchangedOnce() throws Exception {
        User user = newUser(true);
        String code = codeStore.issue(user.getEmail());

        exchangeCode(code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.username").value(user.getEmail()));

        exchangeCode(code).andExpect(status().isUnauthorized());
    }

    @Test
    void oauth2Code_forAccountDisabledMeanwhile_returns403() throws Exception {
        User user = newUser(true);
        String code = codeStore.issue(user.getEmail());
        user.setEnabled(false);
        userRepository.save(user);

        exchangeCode(code).andExpect(status().isForbidden());
    }

    @Test
    void oauth2SuccessHandler_redirectsWithCode_andInvalidatesSession() throws Exception {
        User user = newUser(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        oAuth2SuccessHandler.onAuthenticationSuccess(request, response, googleAuthentication(user.getEmail()));

        assertThat(session.isInvalid()).isTrue();
        assertThat(response.getRedirectedUrl()).startsWith(appUrl + "/oauth2/callback?code=").doesNotContain("token");
        String code = response.getRedirectedUrl().substring(response.getRedirectedUrl().indexOf("code=") + 5);
        exchangeCode(code).andExpect(status().isOk());
    }

    @Test
    void oauth2SuccessHandler_disabledAccount_redirectsToLoginWithError() throws Exception {
        User user = newUser(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        oAuth2SuccessHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, googleAuthentication(user.getEmail()));

        assertThat(response.getRedirectedUrl()).isEqualTo(appUrl + "/login?error=account_disabled");
    }

    @Test
    void googleAccountWithUnverifiedEmail_isRejected() {
        assertThatThrownBy(() -> CustomOAuth2UserService.requireVerifiedEmail(googleUser("a@valkyria.test", false)))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThatCode(() -> CustomOAuth2UserService.requireVerifiedEmail(googleUser("a@valkyria.test", true)))
                .doesNotThrowAnyException();
    }

    // ─── Activación de cuenta ──────────────────────────────────────────────────

    @Test
    void resendActivation_unknownEmail_returns200WithoutSendingEmail() throws Exception {
        resendActivation("nobody-" + UUID.randomUUID() + "@valkyria.test")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void resendActivation_pendingAccountWithExpiredLink_sendsNewWorkingLink() throws Exception {
        User user = newUser(false);
        String expiredToken = verificationTokenService.createVerificationToken(user);
        jdbcTemplate.update("UPDATE verification_tokens SET expiry_date = NOW() - INTERVAL 1 DAY WHERE token = ?", expiredToken);

        confirm(expiredToken).andExpect(status().isBadRequest());

        resendActivation(user.getEmail()).andExpect(status().isOk());
        verify(mailSender).send(any(MimeMessage.class));

        List<String> tokens = verificationTokenService.getTokensByUser(user).stream().map(VerificationToken::getToken).toList();
        assertThat(tokens).hasSize(1).doesNotContain(expiredToken);

        confirm(tokens.getFirst()).andExpect(status().isOk());
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void resendActivation_accountDisabledByAdmin_doesNotReactivate() throws Exception {
        User user = newUser(false); // sin token de activación pendiente

        resendActivation(user.getEmail()).andExpect(status().isOk());

        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(verificationTokenService.getTokensByUser(user)).isEmpty();
    }

    @Test
    void confirm_withEmailChangeToken_doesNotActivateAccount() throws Exception {
        User user = newUser(false);
        String emailChangeToken = verificationTokenService.createEmailChangeToken(user, "new-" + user.getEmail());

        confirm(emailChangeToken).andExpect(status().isBadRequest());

        assertThat(userRepository.findById(user.getId()).orElseThrow().isEnabled()).isFalse();
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private User newUser(boolean enabled) {
        User user = new User();
        user.setEmail("auth-" + UUID.randomUUID().toString().substring(0, 8) + "@valkyria.test");
        user.setPassword(passwordEncoder.encode(TEST_PASSWORD));
        user.setEnabled(enabled);
        user.setFirstName("Auth");
        user.setLastName("Test");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setRoles(List.of(roleRepository.findByName("USER").orElseThrow()));
        return userRepository.save(user);
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private ResultActions getMe(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token));
    }

    private ResultActions exchangeCode(String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/oauth2/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code)));
    }

    private ResultActions resendActivation(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/resend-activation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions confirm(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/confirm").param("token", token));
    }

    private String passwordChangeJson(String current, String newPassword) {
        return "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}"
                .formatted(current, newPassword, newPassword);
    }

    private OAuth2User googleUser(String email, boolean emailVerified) {
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                Map.of("sub", "google-123", "email", email, "email_verified", emailVerified), "sub");
    }

    private OAuth2AuthenticationToken googleAuthentication(String email) {
        OAuth2User principal = googleUser(email, true);
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
