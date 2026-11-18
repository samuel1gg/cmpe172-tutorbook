package edu.sjsu.cmpe172.tutorbook.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import edu.sjsu.cmpe172.tutorbook.ai.rag.KnowledgeBase;

/**
 * RAG retrieval over the REAL knowledge file, with an offline embedding model:
 * the file is split into one chunk per policy, and a question retrieves the
 * chunk that answers it.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:knowledge-test;DB_CLOSE_DELAY=-1",
        "spring.ai.google.genai.api-key=test-key-not-used",
        "spring.ai.google.genai.embedding.api-key=test-key-not-used"})
@Import(FakeAiConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KnowledgeBaseTest {

    @Autowired
    KnowledgeBase knowledgeBase;

    @Test
    void fileIsSplitIntoOneChunkPerPolicySection() {
        List<Document> chunks = knowledgeBase.readChunks();
        assertThat(chunks).hasSize(14);
        assertThat(chunks).extracting(d -> d.getMetadata().get("title"))
                .contains("Opening hours", "Cancellation policy", "What to bring");
    }

    @Test
    void questionsRetrieveTheMatchingPolicy() {
        assertThat(topTitle("How late can I cancel my booking?")).isEqualTo("Cancellation policy");
        assertThat(topTitle("What hours are you open on Saturday?")).isEqualTo("Opening hours");
        assertThat(topTitle("Can classmates join my session?")).isEqualTo("Bringing friends and group sessions");
        assertThat(knowledgeBase.isLoaded()).isTrue();
    }

    private String topTitle(String question) {
        return String.valueOf(knowledgeBase.search(question).get(0).getMetadata().get("title"));
    }
}
