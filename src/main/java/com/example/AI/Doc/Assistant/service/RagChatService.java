package com.example.AI.Doc.Assistant.service;

import com.example.AI.Doc.Assistant.model.ChatMessage;
import com.example.AI.Doc.Assistant.repository.ChatRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RagChatService {

    private final ChatClient chatClient;

    private final VectorStore vectorStore;

    private final ChatRepository chatRepository;

    public RagChatService(ChatClient.Builder builder,
                          VectorStore vectorStore,
                          ChatRepository chatRepository) {

        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
        this.chatRepository = chatRepository;
    }

    public String ask(UUID userId, String question) {

        List<Document> docs = vectorStore.similaritySearch(question);

        String context = docs.stream()
                .map(Document::getText)
                .reduce("", (a,b)->a + "\n" + b);
        String answer = chatClient.prompt()
                .user(question + "\nContext:\n" + context)
                .call()
                .content();

        ChatMessage msg = ChatMessage.builder()
                .userId(userId)
                .question(question)
                .answer(answer)
                .build();

        chatRepository.save(msg);

        return answer;
    }

}
