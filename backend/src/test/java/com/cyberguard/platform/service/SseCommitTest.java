package com.cyberguard.platform.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

class SseCommitTest {
    @Test void monitoringFramesOnlyReachAuthorizedSubscribersAndReconnectReceivesNewFrames() throws Exception {
        var frames = new java.util.ArrayList<java.util.List<String>>();
        SseHubService hub = new SseHubService() {
            @Override protected org.springframework.web.servlet.mvc.method.annotation.SseEmitter createEmitter() {
                var sent = new java.util.ArrayList<String>(); frames.add(sent);
                return new org.springframework.web.servlet.mvc.method.annotation.SseEmitter() {
                    @Override public void send(SseEventBuilder builder) {
                        sent.add(builder.build().stream().map(part -> String.valueOf(part.getData()))
                                .collect(java.util.stream.Collectors.joining()));
                    }
                };
            }
        };
        hub.subscribe(1L, false);
        hub.subscribe(2L, true);
        hub.publishSecurityEvent(com.cyberguard.platform.entity.SecurityEvent.builder().id(1L).build());
        assertTrue(frames.get(0).stream().noneMatch(s -> s.contains("security-event")));
        assertTrue(frames.get(1).stream().anyMatch(s -> s.contains("security-event")));
        hub.subscribe(2L, true);
        hub.heartbeat();
        hub.publishSecurityEvent(com.cyberguard.platform.entity.SecurityEvent.builder().id(2L).build());
        assertTrue(frames.get(2).stream().anyMatch(s -> s.contains("heartbeat")));
        assertTrue(frames.get(2).stream().anyMatch(s -> s.contains("security-event")));
    }

    @Test void publicationWaitsForCommitAndRollbackPublishesNothing() {
        java.util.concurrent.atomic.AtomicInteger sent = new java.util.concurrent.atomic.AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            SseHubService.afterCommit(sent::incrementAndGet);
            assertEquals(0, sent.get());
            var callbacks = TransactionSynchronizationManager.getSynchronizations();
            callbacks.forEach(c -> c.afterCommit());
            assertEquals(1, sent.get());
        } finally { TransactionSynchronizationManager.clear(); }
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            SseHubService.afterCommit(sent::incrementAndGet);
            TransactionSynchronizationManager.getSynchronizations().forEach(c -> c.afterCompletion(1));
            assertEquals(1, sent.get());
        } finally { TransactionSynchronizationManager.clear(); }
    }
}
