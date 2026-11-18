package edu.sjsu.cmpe172.tutorbook.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Is a Gemini API key configured?
 *
 * The key comes only from the GEMINI_API_KEY environment variable (see
 * application.properties); it is never written in the code or committed.
 * Without a key the rest of TutorBook still runs, and the AI features answer
 * with a clear "not configured" message instead of failing at startup.
 */
@Component
public class AiStatus {

    static final String NOT_SET = "not-set";

    private final boolean configured;
    private final String model;

    public AiStatus(@Value("${spring.ai.google.genai.api-key:" + NOT_SET + "}") String apiKey,
                    @Value("${spring.ai.google.genai.chat.model:}") String model) {
        this.configured = apiKey != null && !apiKey.isBlank() && !NOT_SET.equals(apiKey);
        this.model = model;
    }

    public boolean isConfigured() {
        return configured;
    }

    public String model() {
        return model;
    }

    public static final String NOT_CONFIGURED_MESSAGE =
            "The AI assistant is not configured. Set the GEMINI_API_KEY environment variable and restart TutorBook.";
}
