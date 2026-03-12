package com.example.aidocassistant.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfig {

    /**
     * Jina AI is fully OpenAI-compatible (uses /v1/embeddings path), so Spring AI's
     * OpenAiEmbeddingModel works without any custom HTTP handling.
     * Free tier: 1M tokens/month, no credit card required.
     *
     * spring.ai.openai.embedding.enabled=false in application.properties prevents
     * Spring Boot from auto-configuring a conflicting OpenAI embedding bean.
     */
    @Bean
    public EmbeddingModel embeddingModel(@Value("${jina.api-key}") String jinaApiKey) {
        var jinaApi = OpenAiApi.builder()
                .baseUrl("https://api.jina.ai")
                .apiKey(jinaApiKey)
                .build();
        return new OpenAiEmbeddingModel(
                jinaApi,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model("jina-embeddings-v3")
                        .build()
        );
    }
}
