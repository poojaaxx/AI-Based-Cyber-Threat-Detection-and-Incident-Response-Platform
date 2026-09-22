package com.cyberguard.platform.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name = "collector_state") @Data @NoArgsConstructor
public class CollectorState {
    @Id @Column(length = 60) private String collectorId;
    @Column(length = 36) private String sessionId;
    private Instant sessionStartedAt;
    private Instant lastHeartbeatAt;
    private Instant lastSuccessfulSampleAt;
    private String sampleError;
    private String deliveryError;
    private String status;
    private long queuedEvents;
    private long droppedEvents;
    private long receivedEvents;
    private long gapCount;
}
