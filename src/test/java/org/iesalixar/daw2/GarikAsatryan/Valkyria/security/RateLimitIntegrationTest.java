package org.iesalixar.daw2.GarikAsatryan.Valkyria.security;

import org.iesalixar.daw2.GarikAsatryan.Valkyria.AbstractIntegrationTest;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.User;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.RoleRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RateLimitIntegrationTest extends AbstractIntegrationTest {

    // Cada test usa IPs propias: los buckets viven mientras dura el contexto compartido
    private static final AtomicInteger IP_COUNTER = new AtomicInteger();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void login_moreThan10RequestsPerMinuteFromSameIp_returns429WithRetryAfter() throws Exception {
        String ip = newIp();
        for (int i = 0; i < 10; i++) {
            // Cuentas distintas para que no salte el límite por cuenta
            login(ip, "nobody-" + UUID.randomUUID() + "@valkyria.test", "wrong").andExpect(status().isUnauthorized());
        }

        login(ip, "nobody@valkyria.test", "wrong")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("\\d+")))
                .andExpect(jsonPath("$.message").value("Has realizado demasiados intentos. Vuelve a intentarlo más tarde."));

        // Otra IP no se ve afectada
        login(newIp(), "nobody@valkyria.test", "wrong").andExpect(status().isUnauthorized());
    }

    @Test
    void login_tooManyFailuresOnSameAccount_blocksEvenFromOtherIpsAndWithRightPassword() throws Exception {
        User user = newUser();
        for (int i = 0; i < 10; i++) {
            login(newIp(), user.getEmail(), "Wrong1234!").andExpect(status().isUnauthorized());
        }

        login(newIp(), user.getEmail().toUpperCase(), TEST_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void login_successfulLoginsDoNotCountAsFailures() throws Exception {
        User user = newUser();
        for (int i = 0; i < 12; i++) {
            login(newIp(), user.getEmail(), TEST_PASSWORD).andExpect(status().isOk());
        }
    }

    @Test
    void register_moreThan5AttemptsFromSameIp_returns429EvenIfInvalid() throws Exception {
        String ip = newIp();
        for (int i = 0; i < 5; i++) {
            register(ip).andExpect(status().isBadRequest());
        }

        register(ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.not("0")));
    }

    @Test
    void resendActivation_limitedTo3PerIp() throws Exception {
        String ip = newIp();
        for (int i = 0; i < 3; i++) {
            resendActivation(ip).andExpect(status().isOk());
        }

        resendActivation(ip).andExpect(status().isTooManyRequests());
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private ResultActions login(String ip, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(fromIp(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private ResultActions register(String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register").with(fromIp(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    private ResultActions resendActivation(String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/resend-activation").with(fromIp(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody-%s@valkyria.test\"}".formatted(UUID.randomUUID())));
    }

    private RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private String newIp() {
        int n = IP_COUNTER.incrementAndGet();
        return "203.0." + (n / 256) + "." + (n % 256);
    }

    private User newUser() {
        User user = new User();
        user.setEmail("rate-" + UUID.randomUUID().toString().substring(0, 8) + "@valkyria.test");
        user.setPassword(passwordEncoder.encode(TEST_PASSWORD));
        user.setEnabled(true);
        user.setFirstName("Rate");
        user.setLastName("Limit");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setRoles(List.of(roleRepository.findByName("USER").orElseThrow()));
        return userRepository.save(user);
    }
}
