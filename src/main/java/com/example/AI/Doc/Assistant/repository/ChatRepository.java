package com.example.AI.Doc.Assistant.repository;
import com.example.AI.Doc.Assistant.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
public interface ChatRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByUserId(UUID userId);
}
