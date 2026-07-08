package com.cyberguard.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {
    @NotBlank(message = "Message cannot be empty")
    private String message;

    /** Existing session id to continue a conversation; null starts a new one. */
    private Long sessionId;
}
