package org.iesalixar.daw2.GarikAsatryan.Valkyria.security;

import org.iesalixar.daw2.GarikAsatryan.Valkyria.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ErrorResponsesTest extends AbstractIntegrationTest {

    private static final String API = "/api/v1";

    @Test
    void notFound_returns404WithResponseDto() throws Exception {
        mockMvc.perform(get(API + "/artists/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("No se ha encontrado el artista con ID: 999999"));
    }

    @Test
    void notFound_inEnglish_whenAcceptLanguageIsEn() throws Exception {
        mockMvc.perform(get(API + "/artists/999999").header("Accept-Language", "en"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Artist with ID: 999999 was not found"));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(put(API + "/stages/1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{malformed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void nonNumericId_returns400() throws Exception {
        mockMvc.perform(get(API + "/tickets/abc").header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {
        mockMvc.perform(patch(API + "/artists"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_invalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post(API + "/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.email").exists())
                .andExpect(jsonPath("$.data.password").exists());
    }

    @Test
    void register_existingEmail_returns409() throws Exception {
        tokenFor("USER");
        mockMvc.perform(post(API + "/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test-user@valkyria.test","password":"Test1234!","confirmPassword":"Test1234!",
                                 "firstName":"Test","lastName":"Dup","birthDate":"1990-01-01","phone":"600000000"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        tokenFor("USER");
        mockMvc.perform(post(API + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-user@valkyria.test\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
