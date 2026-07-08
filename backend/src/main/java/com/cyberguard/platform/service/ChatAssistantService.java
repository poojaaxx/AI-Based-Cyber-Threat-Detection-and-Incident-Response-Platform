package com.cyberguard.platform.service;

import com.cyberguard.platform.client.AiServiceClient;
import com.cyberguard.platform.dto.request.ChatRequest;
import com.cyberguard.platform.dto.response.ChatResponse;
import com.cyberguard.platform.entity.ChatMessage;
import com.cyberguard.platform.entity.ChatSession;
import com.cyberguard.platform.entity.User;
import com.cyberguard.platform.entity.enums.MessageSender;
import com.cyberguard.platform.exception.ResourceNotFoundException;
import com.cyberguard.platform.repository.ChatMessageRepository;
import com.cyberguard.platform.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI Security Assistant: a chatbot that explains threats, analyzes incidents,
 * explains CVEs and recommends mitigations. Delegates classification/NLP to
 * the Python AI microservice and persists full conversation history.
 */
@Service
@RequiredArgsConstructor
public class ChatAssistantService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AiServiceClient aiServiceClient;

    @Transactional
    public ChatResponse chat(ChatRequest request, User user) {
        ChatSession session = (request.getSessionId() != null)
                ? chatSessionRepository.findById(request.getSessionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"))
                : chatSessionRepository.save(ChatSession.builder()
                    .user(user)
                    .title(request.getMessage().length() > 60 ? request.getMessage().substring(0, 60) + "..." : request.getMessage())
                    .build());

        chatMessageRepository.save(ChatMessage.builder()
                .session(session).sender(MessageSender.USER).message(request.getMessage()).build());

        String context = buildContext(session.getId());
        String reply = aiServiceClient.assistantChat(request.getMessage(), context);

        chatMessageRepository.save(ChatMessage.builder()
                .session(session).sender(MessageSender.ASSISTANT).message(reply).build());

        return ChatResponse.builder().sessionId(session.getId()).reply(reply).build();
    }

    public List<ChatSession> getSessions(Long userId) {
        return chatSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<ChatMessage> getMessages(Long sessionId) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    private String buildContext(Long sessionId) {
        List<ChatMessage> history = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        return history.stream()
                .skip(Math.max(0, history.size() - 6))
                .map(m -> m.getSender() + ": " + m.getMessage())
                .collect(Collectors.joining("\n"));
    }
}
