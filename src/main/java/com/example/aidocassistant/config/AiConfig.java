package com.example.aidocassistant.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Configuration
public class AiConfig {

    /**
     * Give every RestClient (including Spring AI's OpenAI client that calls Groq)
     * a 3-minute read timeout. SimpleClientHttpRequestFactory uses java.net.HttpURLConnection
     * whose setReadTimeout covers the entire response-body read — unlike Apache HC5's
     * setResponseTimeout which only covers the wait for the first byte, causing the
     * JsonEOFException when Groq streams a long response in chunks.
     */
    @Bean
    public RestClientCustomizer restClientCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(30));
            factory.setReadTimeout(Duration.ofSeconds(180));
            builder.requestFactory(factory);
        };
    }

    /**
     * ChatClient is Spring AI's high-level interface for prompting the LLM.
     * The builder is auto-configured using the Groq properties in application.properties
     * (spring.ai.openai.* pointed at api.groq.com/openai).
     * Embeddings are handled separately by EmbeddingConfig (Jina AI cloud API).
     * Zero local dependencies — everything runs in the cloud.
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
