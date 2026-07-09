package com.cyberguard.platform.entity;

import com.cyberguard.platform.entity.enums.Severity;
import com.cyberguard.platform.entity.enums.ThreatStatus;
import com.cyberguard.platform.entity.enums.ThreatType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "threats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Threat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "threat_type", nullable = false)
    private ThreatType threatType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(name = "confidence_score", nullable = false)
    private BigDecimal confidenceScore;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "destination_ip", length = 45)
    private String destinationIp;

    @Column(name = "source_port")
    private Integer sourcePort;

    @Column(name = "destination_port")
    private Integer destinationPort;

    private String protocol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affected_user_id")
    private User affectedUser;

    @Column(name = "recommended_action", length = 500)
    private String recommendedAction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ThreatStatus status = ThreatStatus.DETECTED;

    @Lob
    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    /** 0-100 blended severity+confidence score; distinct from confidenceScore. See RiskScoreService / explainability. */
    @Column(name = "risk_score")
    private BigDecimal riskScore;

    /** Human-readable explanation combining the knowledge-base description with model-derived contributing factors. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String reasoning;

    /** JSON-serialized List&lt;ContributingFactor&gt; from the AI service, for the "Explain AI" view. */
    @Lob
    @Column(name = "contributing_factors", columnDefinition = "TEXT")
    private String contributingFactors;

    /** JSON-serialized List&lt;ContributingFactor&gt; SHAP explanation from the AI service (Phase 3
     * addition). Nullable - older threats predate this field and simply have none; the frontend
     * falls back to the contributingFactors-only display when this is empty. */
    @Lob
    @Column(name = "shap_explanation", columnDefinition = "TEXT")
    private String shapExplanation;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cve_record_id")
    private CveRecord cveRecord;

    @PrePersist
    protected void onCreate() {
        if (detectedAt == null) {
            detectedAt = LocalDateTime.now();
        }
    }
}
