package org.iesalixar.daw2.GarikAsatryan.valkyria.security;

import org.iesalixar.daw2.GarikAsatryan.valkyria.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityAccessTest extends AbstractIntegrationTest {

    private static final String API = "/api/v1";

    static Stream<String> publicGetEndpoints() {
        return Stream.of(
                "/artists", "/artists/1", "/artists/logo",
                "/performances", "/performances/all",
                "/stages", "/stages/1",
                "/ticket-types", "/camping-types",
                "/sponsors/all"
        );
    }

    // Endpoints reservados a MANAGER/ADMIN. Los DELETE usan IDs inexistentes para no modificar datos.
    static Stream<String> managerEndpoints() {
        return Stream.of(
                "GET /tickets", "GET /tickets/1",
                "GET /campings", "GET /campings/1",
                "GET /orders",
                "GET /sponsors", "GET /sponsors/1",
                "GET /users", "GET /users/1",
                "GET /admin/dashboard/stats",
                "DELETE /artists/999999", "DELETE /artists/images/999999",
                "DELETE /stages/999999", "DELETE /sponsors/999999",
                "DELETE /performances/999999", "DELETE /ticket-types/999999",
                "DELETE /camping-types/999999", "DELETE /tickets/999999",
                "DELETE /campings/999999", "DELETE /orders/999999",
                "DELETE /users/999999"
        );
    }

    static Stream<String> authenticatedEndpoints() {
        return Stream.of("GET /users/me", "GET /orders/my-orders");
    }

    @ParameterizedTest
    @MethodSource("publicGetEndpoints")
    void publicEndpoint_anonymous_isAllowed(String path) throws Exception {
        mockMvc.perform(get(API + path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @MethodSource({"managerEndpoints", "authenticatedEndpoints"})
    void protectedEndpoint_anonymous_returns401(String endpoint) throws Exception {
        mockMvc.perform(build(endpoint))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @ParameterizedTest
    @MethodSource("managerEndpoints")
    void managerEndpoint_user_returns403(String endpoint) throws Exception {
        String token = tokenFor("USER");
        mockMvc.perform(build(endpoint).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @ParameterizedTest
    @MethodSource("managerEndpoints")
    void managerEndpoint_admin_isAllowed(String endpoint) throws Exception {
        String token = tokenFor("ADMIN");
        int status = mockMvc.perform(build(endpoint).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
        // 200 o 404 (IDs inexistentes); nunca un rechazo de seguridad ni un error del servidor
        assertThat(status).isNotIn(401, 403).isLessThan(500);
    }

    @ParameterizedTest
    @MethodSource("authenticatedEndpoints")
    void authenticatedEndpoint_user_isAllowed(String endpoint) throws Exception {
        String token = tokenFor("USER");
        mockMvc.perform(build(endpoint).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void managerRole_canAccessManagerEndpoints() throws Exception {
        String token = tokenFor("MANAGER");
        mockMvc.perform(get(API + "/tickets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void invalidToken_onPublicEndpoint_isIgnored() throws Exception {
        mockMvc.perform(get(API + "/artists").header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidToken_onProtectedEndpoint_returns401() throws Exception {
        mockMvc.perform(get(API + "/users/me").header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder build(String endpoint) {
        String[] parts = endpoint.split(" ", 2);
        return request(HttpMethod.valueOf(parts[0]), API + parts[1]);
    }
}
