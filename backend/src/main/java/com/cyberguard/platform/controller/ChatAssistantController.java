package com.cyberguard.platform.controller;

import com.cyberguard.platform.dto.request.ChatRequest;
import com.cyberguard.platform.dto.response.ChatResponse;
import com.cyberguard.platform.entity.ChatMessage;
import com.cyberguard.platform.entity.ChatSession;
import com.cyberguard.platform.security.CustomUserDetails;
import com.cyberguard.platform.service.ChatAssistantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
@Tag(name = "AI Security Assistant", description = "Conversational assistant for threat/CVE explanations and mitigation guidance")
public class ChatAssistantController {

    private final ChatAssistantService chatAssistantService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request,
                                               @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(chatAssistantService.chat(request, principal.getUser()));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSession>> getSessions(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(chatAssistantService.getSessions(principal.getId()));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<ChatMessage>> getMessages(@PathVariable Long sessionId) {
        return ResponseEntity.ok(chatAssistantService.getMessages(sessionId));
    }
}
