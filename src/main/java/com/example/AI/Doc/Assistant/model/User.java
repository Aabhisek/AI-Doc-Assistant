package com.example.AI.Doc.Assistant.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

        @Id
        @GeneratedValue
        private UUID id;

        @Column(nullable = false, unique = true)
        private String email;

        @Column(nullable = false)
        private String passwordHash;

        private Instant createdAt;

        @PrePersist
        void onCreate() {
            createdAt = Instant.now();
        }
}
