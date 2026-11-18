package edu.sjsu.cmpe172.tutorbook.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.sjsu.cmpe172.tutorbook.ai.reminder.ReminderAgent;
import edu.sjsu.cmpe172.tutorbook.ai.rag.PolicyQaService;
import edu.sjsu.cmpe172.tutorbook.exception.BadRequestException;

/**
 *   GET  /api/ask?q=...                grounded Q&A over the policy/FAQ file (public)
 *   POST /admin/agents/reminders/run   manual trigger for Agent 2 (ADMIN only)
 */
@RestController
public class AiApiController {

    private final PolicyQaService policyQa;
    private final ReminderAgent reminderAgent;

    public AiApiController(PolicyQaService policyQa, ReminderAgent reminderAgent) {
        this.policyQa = policyQa;
        this.reminderAgent = reminderAgent;
    }

    @GetMapping("/api/ask")
    public PolicyQaService.Answer ask(@RequestParam("q") String question) {
        String q = question == null ? "" : question.strip();
        if (q.isEmpty() || q.length() > 500) {
            throw new BadRequestException("Question must be 1-500 characters.");
        }
        return policyQa.ask(q);
    }

    @PostMapping("/admin/agents/reminders/run")
    public ReminderAgent.RunReport runReminders() {
        return reminderAgent.run("manual");
    }
}
