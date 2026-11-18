package edu.sjsu.cmpe172.tutorbook.ai.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import edu.sjsu.cmpe172.tutorbook.ai.AiStatus;

/**
 * The knowledge source for grounded Q&A (RAG).
 *
 * INGEST (once, at startup): read knowledge/tutorbook-policies.md, split it into
 * one chunk per "## " section (each section is one self-contained policy, so a
 * chunk never mixes two topics), embed every chunk with the Gemini embedding
 * model and store the vectors in the SimpleVectorStore.
 *
 * RETRIEVE: embed the question and return the most similar chunks. These
 * chunks are what the answer is grounded on.
 *
 * If the key is missing or Gemini is unreachable at startup, ingestion is
 * retried on the first question instead of failing the whole application.
 */
@Component
public class KnowledgeBase {

    public static final int TOP_K = 4;
    private static final Logger log = LoggerFactory.getLogger(KnowledgeBase.class);

    private final SimpleVectorStore vectorStore;
    private final Resource source;
    private final AiStatus aiStatus;
    private volatile boolean loaded;
    private int chunkCount;

    public KnowledgeBase(SimpleVectorStore vectorStore,
                         @Value("${tutorbook.ai.knowledge-file:classpath:knowledge/tutorbook-policies.md}") Resource source,
                         AiStatus aiStatus) {
        this.vectorStore = vectorStore;
        this.source = source;
        this.aiStatus = aiStatus;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        if (!aiStatus.isConfigured()) {
            log.info("GEMINI_API_KEY not set: knowledge base not embedded (AI features disabled)");
            return;
        }
        try {
            ensureLoaded();
        } catch (RuntimeException e) {
            log.warn("Could not embed the knowledge base at startup ({}); will retry on first question", e.toString());
        }
    }

    /** Embeds the file into the vector store once. */
    public synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        List<Document> chunks = readChunks();
        vectorStore.add(chunks);
        chunkCount = chunks.size();
        loaded = true;
        log.info("Knowledge base loaded: {} chunks from {}", chunkCount, source.getFilename());
    }

    /** Splits the markdown file into one Document per "## " section, keeping the title as metadata. */
    public List<Document> readChunks() {
        String text;
        try {
            text = source.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read knowledge file " + source, e);
        }
        return split(text, source.getFilename());
    }

    static List<Document> split(String markdown, String fileName) {
        List<Document> docs = new ArrayList<>();
        String title = null;
        StringBuilder body = new StringBuilder();
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("## ")) {
                addChunk(docs, title, body, fileName);
                title = line.substring(3).strip();
                body.setLength(0);
            } else if (title != null) {
                body.append(line).append('\n');
            }
        }
        addChunk(docs, title, body, fileName);
        return docs;
    }

    private static void addChunk(List<Document> docs, String title, StringBuilder body, String fileName) {
        if (title == null || body.toString().isBlank()) {
            return;
        }
        String text = title + "\n" + body.toString().strip();
        docs.add(new Document(text, Map.of("title", title, "source", fileName)));
    }

    /** The TOP_K chunks most similar to the question. */
    public List<Document> search(String question) {
        ensureLoaded();
        return vectorStore.similaritySearch(SearchRequest.builder().query(question).topK(TOP_K).build());
    }

    /** search() formatted for a tool result. */
    public String searchAsText(String question) {
        if (!aiStatus.isConfigured()) {
            return AiStatus.NOT_CONFIGURED_MESSAGE;
        }
        List<Document> hits = search(question);
        if (hits.isEmpty()) {
            return "No matching policy found.";
        }
        return hits.stream().map(d -> "[" + d.getMetadata().get("title") + "]\n" + d.getText())
                .collect(Collectors.joining("\n\n"));
    }

    public boolean isLoaded() {
        return loaded;
    }

    public int chunkCount() {
        return chunkCount;
    }
}
