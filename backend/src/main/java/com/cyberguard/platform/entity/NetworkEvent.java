package com.cyberguard.platform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "network_events", uniqueConstraints = @UniqueConstraint(name = "uq_network_delivery", columnNames = {"collector_id", "session_id", "sequence_number"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NetworkEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "destination_ip", length = 45)
    private String destinationIp;

    @Column(name = "source_port")
    private Integer sourcePort;

    @Column(name = "destination_port")
    private Integer destinationPort;

    private String protocol;
    private String localIp;
    private Integer localPort;
    private String remoteIp;
    private Integer remotePort;
    private String tcpState;
    private Long processId;
    private String processName;
    private java.time.Instant connectionCreatedAt;
    private java.time.Instant observedAt;
    private java.time.Instant ingestedAt;
    @Column(name = "collector_id", length = 60)
    private String collectorId;
    @Column(name = "session_id", length = 36)
    private String sessionId;
    @Column(name = "sequence_number")
    private Long sequence;
    @Column(length = 36)
    private String connectionId;
    private String eventType;
    private String ruleId;


    @Column(name = "bytes_transferred")
    private Long bytesTransferred;

    @Column(name = "packet_count")
    private Integer packetCount;

    @Column(nullable = false)
    @Builder.Default
    private Boolean flagged = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
