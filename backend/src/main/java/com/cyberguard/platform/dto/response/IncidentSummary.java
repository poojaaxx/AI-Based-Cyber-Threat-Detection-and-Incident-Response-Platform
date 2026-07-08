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
public class IncidentSummary {
    private Long id;
    private String incidentNumber;
    private String title;
    private String severity;
    private String status;
    private String assignedToUsername;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
