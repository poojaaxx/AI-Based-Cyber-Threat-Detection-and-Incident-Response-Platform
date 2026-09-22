package com.cyberguard.platform.service;
import com.cyberguard.platform.dto.request.CollectorRequests.*;
import com.cyberguard.platform.entity.*;
import com.cyberguard.platform.repository.*;
import com.cyberguard.platform.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class NetworkCollectorService {
    private final CollectorStateRepository collectors;
    private final NetworkEventRepository networks;
    private final SecurityEventRepository events;
    private final SseHubService sse;
    @Value("${app.collector.enabled:false}") private boolean enabled;
    @Value("${app.collector.id:windows-local}") private String collectorId;
    @Value("${app.collector.demo-rule-enabled:false}") private boolean demoEnabled;
    @Value("${app.collector.demo-port:19090}") private int demoPort;

    @EventListener(ApplicationReadyEvent.class) @Transactional
    public void initialize() {
        if (!collectors.existsById(collectorId)) {
            CollectorState c = new CollectorState(); c.setCollectorId(collectorId);
            c.setStatus(enabled ? "STARTING" : "DISABLED"); collectors.save(c);
        }
    }
    @Transactional
    public CollectorState start(Start request) {
        checkCollector(request.collectorId());
        CollectorState c = collectors.lockById(collectorId).orElseGet(() -> {
            CollectorState n = new CollectorState(); n.setCollectorId(collectorId); return n;
        });
        if (!request.sessionId().equals(c.getSessionId())) {
            c.setSessionId(request.sessionId()); c.setSessionStartedAt(Instant.now());
            c.setLastSuccessfulSampleAt(null); c.setSampleError(null); c.setDeliveryError(null); c.setQueuedEvents(0);
            c.setDroppedEvents(0); c.setGapCount(0); c.setReceivedEvents(0); c.setStatus("STARTING");
        }
        c.setLastHeartbeatAt(Instant.now()); collectors.save(c); publish(c); return c;
    }
    @Transactional
    public CollectorState heartbeat(Heartbeat request) {
        CollectorState c = session(request.collectorId(), request.sessionId());
        Instant now = Instant.now();
        c.setLastHeartbeatAt(now);
        if (request.lastSuccessfulSampleAt() != null) {
            if (request.lastSuccessfulSampleAt().isBefore(c.getSessionStartedAt().minusSeconds(60)))
                throw new BadRequestException("Sample time predates collector session");
            if (c.getLastSuccessfulSampleAt() == null || request.lastSuccessfulSampleAt().isAfter(c.getLastSuccessfulSampleAt()))
                c.setLastSuccessfulSampleAt(request.lastSuccessfulSampleAt());
        }
        c.setSampleError(request.sampleError()); c.setDeliveryError(request.deliveryError()); c.setQueuedEvents(request.queuedEvents());
        c.setDroppedEvents(Math.max(c.getDroppedEvents(), request.droppedEvents()));
        c.setGapCount(Math.max(c.getGapCount(), request.gapCount()));
        c.setStatus(status(c, now)); collectors.save(c); publish(c); return c;
    }
    @Transactional
    public Map<String, Integer> ingest(Batch batch) {
        CollectorState c = session(batch.collectorId(), batch.sessionId());
        int inserted = 0;
        for (Observation o : batch.observations()) {
            Instant now = Instant.now();
            if (o.observedAt().isAfter(now.plusSeconds(60)) || o.observedAt().isBefore(now.minus(Duration.ofDays(1)))
                    || (o.connectionCreatedAt() != null && o.connectionCreatedAt().isAfter(o.observedAt().plusSeconds(60))))
                throw new BadRequestException("Invalid observation timestamp");
            if (networks.existsByCollectorIdAndSessionIdAndSequence(collectorId, batch.sessionId(), o.sequence())) continue;
            NetworkEvent n = NetworkEvent.builder().localIp(o.localIp()).localPort(o.localPort())
                    .remoteIp(o.remoteIp()).remotePort(o.remotePort()).protocol("TCP").tcpState(o.tcpState().name())
                    .processId(o.processId()).processName(o.processName()).connectionCreatedAt(o.connectionCreatedAt())
                    .observedAt(o.observedAt()).ingestedAt(now).collectorId(collectorId).sessionId(batch.sessionId())
                    .sequence(o.sequence()).connectionId(o.connectionId()).eventType(o.eventType().name()).build();
            boolean matched = demoMatch(n);
            if (matched) n.setRuleId("LOCAL_DEMO_ENDPOINT_V1");
            n = networks.save(n);
            SecurityEvent e = SecurityEvent.builder().source("NETWORK").networkEvent(n)
                    .eventType(n.getEventType()).outcome("OBSERVED").detectorType(matched ? "RULE" : "EVENT")
                    .result(matched ? "Controlled demo endpoint matched; not classified as malicious" : result(o.eventType()))
                    .observedAt(o.observedAt()).ingestedAt(now).detectedAt(matched ? now : null).build();
            sse.publishSecurityEvent(events.save(e)); inserted++;
        }
        c.setReceivedEvents(c.getReceivedEvents() + inserted);
        return Map.of("accepted", batch.observations().size(), "inserted", inserted);
    }
    private boolean demoMatch(NetworkEvent n) {
        if (!demoEnabled || !"Established".equals(n.getTcpState()) || !"127.0.0.1".equals(n.getLocalIp())
                || !"127.0.0.1".equals(n.getRemoteIp()) || n.getRemotePort() != demoPort) return false;
        if (!Set.of("CONNECTION_OBSERVED", "CONNECTION_STATE_CHANGED").contains(n.getEventType())) return false;
        if (networks.existsByCollectorIdAndSessionIdAndConnectionIdAndEventType(collectorId,n.getSessionId(),n.getConnectionId(),"CONNECTION_PRESENT")) return false;
        if ("CONNECTION_STATE_CHANGED".equals(n.getEventType()) && !networks.existsByCollectorIdAndSessionIdAndConnectionIdAndEventType(
                collectorId,n.getSessionId(),n.getConnectionId(),"CONNECTION_OBSERVED")) return false;
        return !networks.existsByCollectorIdAndSessionIdAndConnectionIdAndRuleIdIsNotNull(collectorId,n.getSessionId(),n.getConnectionId());
    }
    private String result(EventType type) {
        return switch (type) {
            case CONNECTION_PRESENT -> "TCP connection present at baseline; no threat determination";
            case CONNECTION_STATE_CHANGED -> "TCP state changed; no threat determination";
            case CONNECTION_NO_LONGER_OBSERVED -> "TCP connection no longer observed; exact close time unknown";
            default -> "TCP connection observed; no threat determination";
        };
    }
    private void checkCollector(String id) {
        if (!enabled || !collectorId.equals(id)) throw new BadRequestException("Collector is disabled or unknown");
    }
    private CollectorState session(String id, String session) {
        checkCollector(id);
        CollectorState c = collectors.lockById(id).orElseThrow(() -> new BadRequestException("Start collector session first"));
        if (!session.equals(c.getSessionId())) throw new BadRequestException("Collector session is no longer active");
        return c;
    }
    private String status(CollectorState c, Instant now) {
        if (!enabled) return "DISABLED";
        if (c.getLastHeartbeatAt() == null) return "STARTING";
        if (c.getLastHeartbeatAt().isBefore(now.minusSeconds(15))) return "UNAVAILABLE";
        if (c.getSampleError() != null || c.getDeliveryError() != null) return "DEGRADED";
        if (c.getLastSuccessfulSampleAt() == null) return "STARTING";
        if (c.getLastSuccessfulSampleAt().isBefore(now.minusSeconds(15)) || c.getQueuedEvents() > 100) return "DEGRADED";
        return "AVAILABLE";
    }
    @Transactional(readOnly = true)
    public CollectorState health() {
        CollectorState c = collectors.findById(collectorId).orElseGet(() -> { CollectorState n = new CollectorState(); n.setCollectorId(collectorId); return n; });
        // Return a detached snapshot; reading health must not mutate persistence.
        CollectorState copy = snapshot(c); copy.setStatus(status(c, Instant.now())); return copy;
    }
    @Scheduled(fixedDelay = 5000) @Transactional
    public void checkHealth() {
        collectors.lockById(collectorId).ifPresent(c -> {
            String next = status(c, Instant.now());
            if (!next.equals(c.getStatus())) { c.setStatus(next); publish(c); }
        });
    }
    private void publish(CollectorState c) { sse.publishCollectorStatus(snapshot(c)); }
    private CollectorState snapshot(CollectorState c) {
        CollectorState n = new CollectorState();
        org.springframework.beans.BeanUtils.copyProperties(c, n); return n;
    }
}
