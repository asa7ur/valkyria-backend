package org.iesalixar.daw2.GarikAsatryan.valkyria;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ValkyriaApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }

    @Test
    void apiDocs_showProjectVersionFromPom() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.version").value(org.hamcrest.Matchers.matchesPattern("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?")));
    }

}
