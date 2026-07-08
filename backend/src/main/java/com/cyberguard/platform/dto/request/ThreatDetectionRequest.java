package com.cyberguard.platform.dto.request;

import lombok.Data;

import java.util.Map;

/**
 * Raw network/event features submitted to the backend, which forwards them
 * to the AI service for classification before persisting the resulting Threat.
 */
@Data
public class ThreatDetectionRequest {
    private String sourceIp;
    private String destinationIp;
    private Integer sourcePort;
    private Integer destinationPort;
    private String protocol;
    private Long bytesTransferred;
    private Integer packetCount;
    private Integer durationMs;
    private Integer failedLogins;
    private Boolean flagged;
    private Map<String, Object> extraFeatures;
}
