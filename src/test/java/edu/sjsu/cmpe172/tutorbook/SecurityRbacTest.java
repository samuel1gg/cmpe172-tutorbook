package edu.sjsu.cmpe172.tutorbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import edu.sjsu.cmpe172.tutorbook.security.JdbcUserDetailsService;

/** Login (BCrypt + session) and role-based access control. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityRbacTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcUserDetailsService users;

    @Autowired
    JdbcClient jdbc;

    @Test
    void passwordsAreStoredAsBcryptHashesNotPlaintext() {
        var hashes = jdbc.sql("SELECT password_hash FROM users").query(String.class).list();
        assertThat(hashes).isNotEmpty().allMatch(h -> h.startsWith("$2a$10$"));
        assertThat(hashes).noneMatch(h -> h.contains("password123"));
    }

    @Test
    void loginWithCorrectPasswordCreatesSession() throws Exception {
        mvc.perform(formLogin("/login").userParameter("email").user("alice@student.sjsu.edu").password("password123"))
                .andExpect(authenticated().withRoles("CUSTOMER"));
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        mvc.perform(formLogin("/login").userParameter("email").user("alice@student.sjsu.edu").password("wrong"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrlPattern("/login?error*"));
    }

    @Test
    void anonymousUserIsSentToLogin() throws Exception {
        mvc.perform(get("/my/appointments")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/provider/slots")).andExpect(status().is3xxRedirection());
    }

    @Test
    void customerCannotUseProviderEndpoints() throws Exception {
        var alice = users.loadUserByUsername("alice@student.sjsu.edu");
        mvc.perform(get("/provider/slots").with(user(alice))).andExpect(status().isForbidden());
        mvc.perform(post("/provider/slots/1/remove").with(user(alice)).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void providerCannotUseCustomerEndpoints() throws Exception {
        var kim = users.loadUserByUsername("j.kim@tutorbook.edu");
        mvc.perform(get("/slots/1/book").with(user(kim))).andExpect(status().isForbidden());
        mvc.perform(post("/slots/1/book").with(user(kim)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/my/appointments").with(user(kim))).andExpect(status().isForbidden());
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        var alice = users.loadUserByUsername("alice@student.sjsu.edu");
        mvc.perform(post("/slots/1/book").with(user(alice))).andExpect(status().isForbidden());
    }
}
