package com.cyberguard.platform.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory hub for Server-Sent Events. Holds one emitter per connected browser
 * tab, keyed by user id, and fans out two kinds of events:
 * - "notification": pushed to a single user (their own notification feed)
 * - "dashboard-update": broadcast to every connected client as a lightweight
 *   signal; the frontend reacts by re-fetching the existing REST endpoints
 *   rather than duplicating dashboard computation logic here.
 *
 * No external message broker is used - this is a single-instance in-memory
 * registry, adequate for this project's deployment model.
 */
@Service
@Slf4j
public class SseHubService {

    private final Map<Long, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();
    private final java.util.Set<SseEmitter> monitoringEmitters = ConcurrentHashMap.newKeySet();
    private final Map<SseEmitter, java.util.function.BooleanSupplier> accessChecks = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId, boolean monitoringAllowed, java.util.function.BooleanSupplier accessCheck) {
        SseEmitter emitter = subscribe(userId, monitoringAllowed);
        accessChecks.put(emitter, accessCheck);
        return emitter;
    }

    public SseEmitter subscribe(Long userId, boolean monitoringAllowed) {
        SseEmitter emitter = subscribe(userId);
        if (monitoringAllowed) monitoringEmitters.add(emitter);
        return emitter;
    }

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = createEmitter();
        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(ex -> removeEmitter(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException ex) {
            removeEmitter(userId, emitter);
        }
        return emitter;
    }

    protected SseEmitter createEmitter() { return new SseEmitter(0L); }

    public void pushNotification(Long userId, Object payload) {
        afterCommit(() -> sendTo(userEmitters.getOrDefault(userId, List.of()), "notification", payload));
    }

    /** Signals every connected client that dashboard data has changed; carries no heavy payload by design. */
    public void broadcastDashboardUpdate(String eventType) {
        Map<String, String> signal = Map.of("type", eventType, "timestamp", Instant.now().toString());
        afterCommit(() -> userEmitters.values().forEach(emitters -> sendTo(emitters, "dashboard-update", signal)));
    }

    public void publishSecurityEvent(com.cyberguard.platform.entity.SecurityEvent event) {
        afterCommit(() -> sendTo(List.copyOf(monitoringEmitters), "security-event", event));
    }

    public void publishCollectorStatus(com.cyberguard.platform.entity.CollectorState state) {
        afterCommit(() -> sendTo(List.copyOf(monitoringEmitters), "collector-status", state));
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        userEmitters.values().forEach(emitters -> sendTo(emitters, "heartbeat", Instant.now().toString()));
    }

    static void afterCommit(Runnable publish) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { publish.run(); }
                    });
        } else {
            publish.run();
        }
    }

    private void sendTo(List<SseEmitter> emitters, String eventName, Object payload) {
        for (SseEmitter emitter : emitters) {
            try {
                if (!accessChecks.getOrDefault(emitter, () -> true).getAsBoolean()) {
                    emitter.complete();
                    userEmitters.keySet().forEach(id -> removeEmitter(id, emitter));
                    continue;
                }
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (Exception ex) {
                emitter.complete();
                userEmitters.keySet().forEach(id -> removeEmitter(id, emitter));
            }
        }
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        monitoringEmitters.remove(emitter);
        accessChecks.remove(emitter);
        userEmitters.computeIfPresent(userId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
