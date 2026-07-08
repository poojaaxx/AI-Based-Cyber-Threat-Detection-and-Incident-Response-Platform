package com.cyberguard.platform.entity;

import com.cyberguard.platform.entity.enums.IocType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ioc_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IocRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "ioc_type", nullable = false)
    private IocType iocType;

    @Column(name = "ioc_value", nullable = false, length = 500)
    private String iocValue;

    @Column(name = "threat_type", length = 100)
    private String threatType;

    @Column(name = "confidence_score")
    private BigDecimal confidenceScore;

    private String source;

    @Column(name = "first_seen")
    private LocalDateTime firstSeen;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
