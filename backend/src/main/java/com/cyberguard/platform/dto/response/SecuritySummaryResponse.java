package com.cyberguard.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecuritySummaryResponse {
    private String postureLevel;
    private String summary;
    private List<String> keyFindings;
    private LocalDateTime generatedAt;
}
