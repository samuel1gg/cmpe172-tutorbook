package edu.sjsu.cmpe172.tutorbook.ai;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milestone 4 AI wiring.
 *
 * The ChatModel (Gemini) and EmbeddingModel (Gemini embeddings) come from the
 * Spring AI Google GenAI starters, configured only with an API key. Here we add
 * the vector store used for retrieval-augmented generation: SimpleVectorStore
 * keeps the embedded knowledge chunks in memory, which is plenty for a small
 * policy/FAQ file and needs no extra container.
 */
@Configuration
public class AiConfig {

    @Bean
    public SimpleVectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
