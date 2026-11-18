package edu.sjsu.cmpe172.tutorbook.ai.rag;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;

import edu.sjsu.cmpe172.tutorbook.ai.AiStatus;

/**
 * GROUNDED Q&A (retrieval-augmented generation).
 *
 * QuestionAnswerAdvisor runs before the model call: it embeds the question,
 * fetches the TOP_K most similar knowledge chunks from the vector store and
 * appends them to the prompt as context. The system prompt tells Gemini to
 * answer ONLY from that context, so answers are grounded in our policy file
 * rather than the model's general knowledge. The titles of the retrieved
 * chunks are returned as sources so the answer can be checked.
 */
@Service
public class PolicyQaService {

    private static final Logger log = LoggerFactory.getLogger(PolicyQaService.class);

    static final String SYSTEM_PROMPT = """
            You answer questions about the TutorBook tutoring center.
            Use ONLY the context information that is provided with the question.
            If the context does not contain the answer, say "I don't know based on TutorBook's
            policies" and suggest contacting the front desk at tutorbook@sjsu.edu.
            Do not invent hours, prices, rules or names. Answer in 1-4 short sentences.
            """;

    public record Answer(String question, String answer, List<String> sources) {
    }

    private final ChatClient chatClient;
    private final KnowledgeBase knowledgeBase;
    private final AiStatus aiStatus;

    public PolicyQaService(ChatClient.Builder builder, SimpleVectorStore vectorStore,
                           KnowledgeBase knowledgeBase, AiStatus aiStatus) {
        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().topK(KnowledgeBase.TOP_K).build())
                        .build())
                .build();
        this.knowledgeBase = knowledgeBase;
        this.aiStatus = aiStatus;
    }

    public Answer ask(String question) {
        if (!aiStatus.isConfigured()) {
            return new Answer(question, AiStatus.NOT_CONFIGURED_MESSAGE, List.of());
        }
        try {
            knowledgeBase.ensureLoaded();
            ChatClientResponse response = chatClient.prompt().user(question).call().chatClientResponse();
            String answer = response.chatResponse().getResult().getOutput().getText();
            return new Answer(question, answer, sources(response));
        } catch (RuntimeException e) {
            log.warn("Policy Q&A failed: {}", e.toString());
            return new Answer(question, "Sorry, the assistant is unavailable right now. Please try again later.",
                    List.of());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> sources(ChatClientResponse response) {
        Object docs = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        if (!(docs instanceof List<?> list)) {
            return List.of();
        }
        return ((List<Document>) list).stream()
                .map(d -> String.valueOf(d.getMetadata().get("title")))
                .distinct().toList();
    }
}
