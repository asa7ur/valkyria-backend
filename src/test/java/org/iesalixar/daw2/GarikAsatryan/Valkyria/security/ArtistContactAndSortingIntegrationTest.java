package org.iesalixar.daw2.GarikAsatryan.Valkyria.security;

import org.iesalixar.daw2.GarikAsatryan.Valkyria.AbstractIntegrationTest;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Artist;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.ArtistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ArtistContactAndSortingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ArtistRepository artistRepository;

    @Test
    void artistList_anonymous_hidesContactData() throws Exception {
        mockMvc.perform(get("/api/v1/artists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").exists())
                .andExpect(jsonPath("$.data[*].email").isEmpty())
                .andExpect(jsonPath("$.data[*].phone").isEmpty());
    }

    @Test
    void artistList_regularUser_hidesContactData() throws Exception {
        mockMvc.perform(get("/api/v1/artists").header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].email").isEmpty());
    }

    @Test
    void artistList_manager_includesContactData() throws Exception {
        mockMvc.perform(get("/api/v1/artists").header("Authorization", "Bearer " + tokenFor("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").exists());
    }

    @Test
    void artistDetail_hidesContactForAnonymous_andShowsItForAdmin() throws Exception {
        Artist artist = artistRepository.findAll().stream()
                .filter(a -> a.getEmail() != null)
                .findFirst().orElseThrow();

        mockMvc.perform(get("/api/v1/artists/" + artist.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(artist.getName()))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.phone").doesNotExist());

        mockMvc.perform(get("/api/v1/artists/" + artist.getId()).header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(artist.getEmail()));
    }

    @Test
    void sortingByNotAllowedField_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("order", "password")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No se puede ordenar por 'password'"));

        mockMvc.perform(get("/api/v1/artists").param("order", "noexiste"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sortingByAllowedField_works() throws Exception {
        mockMvc.perform(get("/api/v1/artists").param("order", "name").param("orderBy", "desc"))
                .andExpect(status().isOk());
    }
}
