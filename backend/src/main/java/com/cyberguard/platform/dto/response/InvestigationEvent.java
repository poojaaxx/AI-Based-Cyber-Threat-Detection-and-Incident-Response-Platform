package com.cyberguard.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One entry in the unified chronological attack investigation timeline. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestigationEvent {
    private LocalDateTime timestamp;
    private String eventType;
    private String title;
    private String description;
    private String actor;
}
