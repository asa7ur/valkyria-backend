package org.iesalixar.daw2.GarikAsatryan.Valkyria;

import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.User;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.RoleRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de los tests de integración: contexto completo contra MariaDB en Docker (Testcontainers).
 * Todas las subclases comparten contexto y contenedor, así que no deben declarar beans o mocks propios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    protected static final String TEST_PASSWORD = "Test1234!";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Ningún test debe enviar emails reales
    @MockitoBean
    protected JavaMailSender mailSender;

    /**
     * Crea (si no existe) un usuario activo con el rol indicado y devuelve su JWT haciendo login real.
     */
    protected String tokenFor(String roleName) throws Exception {
        String email = "test-" + roleName.toLowerCase() + "@valkyria.test";
        if (userRepository.findByEmail(email).isEmpty()) {
            User user = new User();
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(TEST_PASSWORD));
            user.setEnabled(true);
            user.setFirstName("Test");
            user.setLastName(roleName);
            user.setBirthDate(LocalDate.of(1990, 1, 1));
            user.setRoles(List.of(roleRepository.findByName(roleName).orElseThrow()));
            userRepository.save(user);
        }

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }
}
