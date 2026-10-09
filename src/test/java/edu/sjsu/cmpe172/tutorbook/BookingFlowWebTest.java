package edu.sjsu.cmpe172.tutorbook;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import edu.sjsu.cmpe172.tutorbook.security.JdbcUserDetailsService;

/**
 * Full request flow UI -> Controller -> Service -> Repository -> H2 for every
 * Milestone 2 feature, including the HTTP status codes (400, 403, 404, 409).
 * @Transactional rolls each test back so the seed data stays clean.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingFlowWebTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcUserDetailsService users;

    UserDetails alice() {
        return users.loadUserByUsername("alice@student.sjsu.edu");
    }

    UserDetails bob() {
        return users.loadUserByUsername("bob@student.sjsu.edu");
    }

    UserDetails kim() {
        return users.loadUserByUsername("j.kim@tutorbook.edu");
    }

    @Test
    void customerBooksSlotAndSeesConfirmation() throws Exception {
        mvc.perform(post("/slots/1/book").param("notes", "chain rule").with(user(alice())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/appointments/*/confirmation"));

        mvc.perform(get("/slots")).andExpect(content().string(containsString("11 open slot(s) found")));
    }

    @Test
    void bookingAnAlreadyBookedSlotReturns409() throws Exception {
        mvc.perform(post("/slots/1/book").with(user(alice())).with(csrf())).andExpect(status().is3xxRedirection());

        mvc.perform(post("/slots/1/book").with(user(bob())).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("just booked by someone else")));
    }

    @Test
    void invalidInputReturns400() throws Exception {
        mvc.perform(post("/slots/1/book").param("notes", "x".repeat(501)).with(user(alice())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("at most 500 characters")));
        mvc.perform(get("/slots/abc/book").with(user(alice())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownSlotReturns404WithoutStackTrace() throws Exception {
        mvc.perform(post("/slots/9999/book").with(user(alice())).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at edu.sjsu"))));
    }

    @Test
    void myAppointmentsShowsUpcomingAndHistoryWithCompletedStatus() throws Exception {
        mvc.perform(get("/my/appointments").with(user(alice())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Calculus Tutoring")))
                .andExpect(content().string(containsString("COMPLETED")));
    }

    @Test
    void ownerCancelsOwnAppointment() throws Exception {
        mvc.perform(post("/my/appointments/1/cancel").with(user(alice())).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/my/appointments/1/cancel").with(user(alice())).with(csrf()))
                .andExpect(status().isConflict()); // already cancelled
    }

    @Test
    void cancellingSomeoneElsesAppointmentReturns403() throws Exception {
        mvc.perform(post("/my/appointments/1/cancel").with(user(bob())).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmationIsOnlyVisibleToItsCustomerAndProvider() throws Exception {
        mvc.perform(get("/appointments/1/confirmation").with(user(alice()))).andExpect(status().isOk());
        mvc.perform(get("/appointments/1/confirmation").with(user(bob()))).andExpect(status().isForbidden());
        // appointment 1 is with Dr. Patel, not James Kim
        mvc.perform(get("/appointments/1/confirmation").with(user(kim()))).andExpect(status().isForbidden());
    }

    @Test
    void providerCreatesAndRemovesASlot() throws Exception {
        String date = LocalDate.now().plusDays(10).toString();
        mvc.perform(post("/provider/slots").param("serviceId", "2").param("date", date).param("startTime", "15:00")
                        .with(user(kim())).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/provider/slots").with(user(kim())))
                .andExpect(content().string(containsString("3:00 PM - 4:00 PM")));

        // same time again -> 409 conflict
        mvc.perform(post("/provider/slots").param("serviceId", "2").param("date", date).param("startTime", "15:30")
                        .with(user(kim())).with(csrf()))
                .andExpect(status().isConflict());

        // remove an open slot of his (slot 4)
        mvc.perform(post("/provider/slots/4/remove").with(user(kim())).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/slots/4")).andExpect(status().isNotFound());
    }

    @Test
    void providerSlotValidationReturns400() throws Exception {
        mvc.perform(post("/provider/slots").param("serviceId", "2").with(user(kim())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Choose a date")));
    }

    @Test
    void providerCannotRemoveAnotherProvidersSlot() throws Exception {
        mvc.perform(post("/provider/slots/1/remove").with(user(kim())).with(csrf())) // slot 1 is Dr. Patel's
                .andExpect(status().isForbidden());
    }

    @Test
    void providerSeesAppointmentsBookedWithThem() throws Exception {
        mvc.perform(post("/slots/4/book").with(user(alice())).with(csrf())).andExpect(status().is3xxRedirection());

        mvc.perform(get("/provider/appointments").with(user(kim())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alice Nguyen")));
    }
}
