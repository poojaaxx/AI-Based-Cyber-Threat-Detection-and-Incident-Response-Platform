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

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout; client reconnects on drop
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

    public void pushNotification(Long userId, Object payload) {
        sendTo(userEmitters.getOrDefault(userId, List.of()), "notification", payload);
    }

    /** Signals every connected client that dashboard data has changed; carries no heavy payload by design. */
    public void broadcastDashboardUpdate(String eventType) {
        Map<String, String> signal = Map.of("type", eventType, "timestamp", Instant.now().toString());
        userEmitters.values().forEach(emitters -> sendTo(emitters, "dashboard-update", signal));
    }

    private void sendTo(List<SseEmitter> emitters, String eventName, Object payload) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException ex) {
                emitter.complete();
            }
        }
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        userEmitters.computeIfPresent(userId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
