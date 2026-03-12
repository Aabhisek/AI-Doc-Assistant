package com.example.aidocassistant.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class VectorStoreConfig {

    /**
     * Manually creates the PgVectorStore bean so we can pass initializeSchema=true,
     * which auto-creates the required pgvector extension and vector_store table on
     * the first application startup — no manual SQL migrations needed.
     *
     * We exclude Spring AI's default PgVector auto-configuration in application.properties
     * to prevent a conflict with this manual bean definition.
     */
    @Bean
    public PgVectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .initializeSchema(true)
                .dimensions(1024)
                .build();
    }
}
