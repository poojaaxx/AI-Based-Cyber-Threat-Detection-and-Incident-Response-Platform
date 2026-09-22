package com.cyberguard.platform.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** Sanitized authentication feed: deliberately contains no User, password or token. */
@Entity
@Table(name = "security_events", indexes = @Index(name = "idx_security_events_observed", columnList = "observed_at"))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SecurityEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private Long loginAttemptId;
    @Column(nullable = false)
    private String source;
    @Column(nullable = false)
    private String eventType;
    private String username;
    private String sourceIp;
    @Column(nullable = false)
    private String outcome;
    private String detectorType;
    @Column(nullable = false)
    private String result;
    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;
    @Column(nullable = false)
    private Instant ingestedAt;
    private Instant detectedAt;
    private Long processingLatencyMs;
    private Long threatId;
    private Long incidentId;
    @Lob @Column(columnDefinition = "TEXT")
    private String evidence;
}
