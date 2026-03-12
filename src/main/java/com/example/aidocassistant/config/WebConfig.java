package com.example.aidocassistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Allows the React dev server (Vite on port 5173) and the nginx-served
     * production build (port 3000) to call the Spring Boot API (port 8080).
     * Without this, browsers would block the requests due to the same-origin policy.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000", "http://localhost:5173")
                .allowedMethods("GET", "POST", "DELETE")
                .allowedHeaders("*");
    }
}
