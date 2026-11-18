package edu.sjsu.cmpe172.tutorbook.controller;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import edu.sjsu.cmpe172.tutorbook.ai.AiStatus;
import edu.sjsu.cmpe172.tutorbook.ai.agent.BookingChatAgent;
import edu.sjsu.cmpe172.tutorbook.exception.BadRequestException;
import edu.sjsu.cmpe172.tutorbook.security.AppUser;
import jakarta.servlet.http.HttpSession;

/**
 * Agent 1 endpoints (login required; the agent acts as this user).
 *   GET  /assistant        chat page
 *   POST /assistant/chat   {"message": "..."} -> {"reply": "..."}
 *   POST /assistant/reset  start a new conversation
 * Both POSTs need the CSRF token, like every other form in TutorBook.
 */
@Controller
public class AssistantController {

    static final int MAX_MESSAGE_LENGTH = 1000;

    private final BookingChatAgent agent;
    private final AiStatus aiStatus;

    public AssistantController(BookingChatAgent agent, AiStatus aiStatus) {
        this.agent = agent;
        this.aiStatus = aiStatus;
    }

    public record ChatRequest(String message) {
    }

    @GetMapping("/assistant")
    public String page(Model model) {
        model.addAttribute("aiConfigured", aiStatus.isConfigured());
        model.addAttribute("aiModel", aiStatus.model());
        return "assistant";
    }

    @PostMapping("/assistant/chat")
    @ResponseBody
    public Map<String, String> chat(@RequestBody ChatRequest request,
                                    @AuthenticationPrincipal AppUser user, HttpSession session) {
        String message = request == null || request.message() == null ? "" : request.message().strip();
        if (message.isEmpty() || message.length() > MAX_MESSAGE_LENGTH) {
            throw new BadRequestException("Message must be 1-" + MAX_MESSAGE_LENGTH + " characters.");
        }
        String reply = agent.chat(user, BookingChatAgent.conversationId(user, session.getId()), message);
        return Map.of("reply", reply);
    }

    @PostMapping("/assistant/reset")
    @ResponseBody
    public Map<String, String> reset(@AuthenticationPrincipal AppUser user, HttpSession session) {
        agent.reset(BookingChatAgent.conversationId(user, session.getId()));
        return Map.of("reply", "Started a new conversation.");
    }
}
