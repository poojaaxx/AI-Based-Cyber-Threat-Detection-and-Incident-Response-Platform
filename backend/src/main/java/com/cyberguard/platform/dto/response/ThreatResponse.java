package com.cyberguard.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThreatResponse {
    private Long id;
    private String threatType;
    private String severity;
    private BigDecimal confidenceScore;
    private String sourceIp;
    private String destinationIp;
    private Integer sourcePort;
    private Integer destinationPort;
    private String protocol;
    private String recommendedAction;
    private String status;
    private LocalDateTime detectedAt;
    private String cveId;
}
