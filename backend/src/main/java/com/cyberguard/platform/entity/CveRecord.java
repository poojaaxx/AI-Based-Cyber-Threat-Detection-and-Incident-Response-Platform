package com.cyberguard.platform.entity;

import com.cyberguard.platform.entity.enums.Severity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "cve_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CveRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cve_id", nullable = false, unique = true, length = 30)
    private String cveId;

    @Column(nullable = false)
    private String title;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(name = "cvss_score")
    private BigDecimal cvssScore;

    @Column(name = "published_date")
    private LocalDate publishedDate;

    @Lob
    @Column(name = "affected_products", columnDefinition = "TEXT")
    private String affectedProducts;

    @Column(name = "reference_url", length = 500)
    private String referenceUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToMany
    @JoinTable(
            name = "threat_intel_mitre_map",
            joinColumns = @JoinColumn(name = "cve_record_id"),
            inverseJoinColumns = @JoinColumn(name = "technique_id")
    )
    @Builder.Default
    private Set<MitreAttackTechnique> mitreTechniques = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
