package edu.sjsu.cmpe172.tutorbook.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Offline stand-ins for Gemini so the AI tests are fast, free and repeatable:
 *  - ScriptedChatModel answers with whatever the test scripts (text or tool calls),
 *    so we can drive the REAL ChatClient tool-calling loop and our real tools;
 *  - HashingEmbeddingModel turns text into a bag-of-words vector, so similarity
 *    search over the real knowledge file works without the network.
 */
@TestConfiguration
public class FakeAiConfig {

    @Bean
    @Primary
    public ScriptedChatModel scriptedChatModel() {
        return new ScriptedChatModel();
    }

    @Bean
    @Primary
    public EmbeddingModel hashingEmbeddingModel() {
        return new HashingEmbeddingModel();
    }

    /** A ChatModel whose replies are decided by the test. Records every prompt it receives. */
    public static class ScriptedChatModel implements ChatModel {

        private volatile Function<Prompt, AssistantMessage> responder =
                p -> AssistantMessage.builder().content("ok").build();
        public final List<Prompt> prompts = new CopyOnWriteArrayList<>();

        public void respondWith(Function<Prompt, AssistantMessage> responder) {
            this.responder = responder;
            prompts.clear();
        }

        /** Like a real provider: tool-calling options, so the ChatClient runs the tool loop. */
        @Override
        public ChatOptions getDefaultOptions() {
            return ToolCallingChatOptions.builder().build();
        }

        @Override
        public ChatOptions getOptions() {
            return getDefaultOptions();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            return new ChatResponse(List.of(new Generation(responder.apply(prompt))));
        }

        public static AssistantMessage text(String content) {
            return AssistantMessage.builder().content(content).build();
        }

        public static AssistantMessage toolCall(String tool, String argumentsJson) {
            return AssistantMessage.builder().content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall("call-" + tool, "function", tool, argumentsJson)))
                    .build();
        }
    }

    /** Deterministic bag-of-words embedding (256 buckets, L2-normalised). */
    public static class HashingEmbeddingModel implements EmbeddingModel {

        static final int DIM = 256;

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> out = new ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                out.add(new Embedding(vector(request.getInstructions().get(i)), i));
            }
            return new EmbeddingResponse(out);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        static float[] vector(String text) {
            float[] v = new float[DIM];
            for (String word : text.toLowerCase(Locale.US).split("[^a-z0-9]+")) {
                if (word.length() > 3) {
                    String stem = word.length() > 6 ? word.substring(0, 6) : word;   // cancel/cancellation
                    v[Math.floorMod(stem.hashCode(), DIM)] += 1;
                }
            }
            double norm = 0;
            for (float x : v) {
                norm += x * x;
            }
            norm = Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < DIM; i++) {
                    v[i] /= (float) norm;
                }
            }
            return v;
        }
    }
}
