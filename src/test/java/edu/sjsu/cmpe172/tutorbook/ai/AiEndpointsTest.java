package edu.sjsu.cmpe172.tutorbook.ai;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import edu.sjsu.cmpe172.tutorbook.security.AppUser;

/**
 * Access rules for the Milestone 4 endpoints, and graceful behaviour when no
 * GEMINI_API_KEY is configured (the default in the test environment).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiEndpointsTest {

    static final AppUser ALICE = new AppUser(1, "alice@student.sjsu.edu", "x", "Alice Nguyen", "CUSTOMER", null);
    static final AppUser ADMIN = new AppUser(8, "admin@tutorbook.edu", "x", "Center Admin", "ADMIN", null);

    @Autowired
    MockMvc mvc;

    @Test
    void assistantPageNeedsLogin() throws Exception {
        mvc.perform(get("/assistant")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/assistant").with(user(ALICE)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("TutorBook Assistant")));
    }

    @Test
    void chatNeedsCsrfToken() throws Exception {
        mvc.perform(post("/assistant/chat").with(user(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void chatWithoutKeyExplainsItIsNotConfigured() throws Exception {
        mvc.perform(post("/assistant/chat").with(user(ALICE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("GEMINI_API_KEY")));
    }

    @Test
    void emptyChatMessageIsRejected() throws Exception {
        mvc.perform(post("/assistant/chat").with(user(ALICE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void policyQuestionIsPublic() throws Exception {
        mvc.perform(get("/api/ask").param("q", "When are you open?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("When are you open?"));
    }

    @Test
    void onlyAdminCanTriggerTheReminderAgent() throws Exception {
        mvc.perform(post("/admin/agents/reminders/run").with(user(ALICE)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/agents/reminders/run").with(user(ADMIN)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trigger").value(org.hamcrest.Matchers.startsWith("manual")));
    }
}
