package com.cyberguard.platform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A single run of the Threat Simulation Lab. Captures the contextual fields
 * (country, user role, device type, traffic type, threat category hint,
 * description, total login attempts, packet size) that have no home on the
 * core Threat entity, plus a link to the Threat that the existing AI pipeline
 * actually produced. None of the contextual fields are fed to the model.
 */
@Entity
@Table(name = "simulation_runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private User performedBy;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "destination_ip", length = 45)
    private String destinationIp;

    private Integer port;

    @Column(length = 20)
    private String protocol;

    @Column(name = "packet_size")
    private Integer packetSize;

    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts;

    @Column(name = "total_login_attempts")
    private Integer totalLoginAttempts;

    @Column(name = "traffic_type", length = 50)
    private String trafficType;

    @Column(length = 100)
    private String country;

    @Column(name = "user_role", length = 50)
    private String userRole;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "threat_category", length = 50)
    private String threatCategory;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_threat_id")
    private Threat resultThreat;

    /** JSON array of AI-recommended response actions, snapshotted at simulation time. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String recommendations;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
