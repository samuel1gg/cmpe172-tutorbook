package edu.sjsu.cmpe172.tutorbook;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-end through Controller -> Service -> Repository -> H2 for the public read endpoints. */
@SpringBootTest
@AutoConfigureMockMvc
class WebEndpointsTest {

    @Autowired
    MockMvc mvc;

    @Test
    void homePageShowsProvidersAndServicesFromDatabase() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("Dr. Priya Patel")))
                .andExpect(content().string(containsString("Calculus Tutoring")));
    }

    @Test
    void slotsPageListsAvailableSlots() throws Exception {
        mvc.perform(get("/slots"))
                .andExpect(status().isOk())
                .andExpect(view().name("slots"))
                .andExpect(content().string(containsString("12 open slot(s) found")));
    }

    @Test
    void slotsApiPaginatesWithLimitOffset() throws Exception {
        mvc.perform(get("/api/slots").param("size", "5").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(12))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void slotsApiFiltersByProvider() throws Exception {
        mvc.perform(get("/api/slots").param("providerId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.items[0].providerName").value("James Kim"));
    }

    @Test
    void apiErrorsAreProblemDetailsWithoutStackTraces() throws Exception {
        mvc.perform(get("/api/slots/2")) // slot 2 is booked -> not available
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.trace").doesNotExist());
        mvc.perform(get("/api/slots/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
