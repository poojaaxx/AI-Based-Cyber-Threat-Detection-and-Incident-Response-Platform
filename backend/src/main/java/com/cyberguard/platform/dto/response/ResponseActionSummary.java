package com.cyberguard.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResponseActionSummary {
    private Long id;
    private String actionType;
    private String target;
    private String status;
    private String triggeredBy;
    private String details;
    private LocalDateTime createdAt;
}
