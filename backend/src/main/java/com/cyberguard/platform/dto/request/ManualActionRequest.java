package com.cyberguard.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ManualActionRequest {
    @NotBlank(message = "Target is required (IP address, username, or threat id)")
    private String target;
    private String reason;
    private Long threatId;
}
