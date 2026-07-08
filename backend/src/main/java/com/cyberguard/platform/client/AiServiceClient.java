package com.cyberguard.platform.client;

import com.cyberguard.platform.dto.request.ThreatDetectionRequest;
import com.cyberguard.platform.dto.response.AiPredictionResponse;
import com.cyberguard.platform.dto.response.ContributingFactor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin client for the Python FastAPI AI microservice. Falls back to a
 * deterministic heuristic classification if the AI service is unreachable,
 * so threat ingestion never hard-fails because of a downstream outage.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiServiceClient {

    private final WebClient aiServiceWebClient;

    public AiPredictionResponse predictThreat(ThreatDetectionRequest request) {
        try {
            return aiServiceWebClient.post()
                    .uri("/api/v1/predict")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AiPredictionResponse.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
        } catch (Exception ex) {
            log.warn("AI service unavailable, using fallback heuristic classification: {}", ex.getMessage());
            return fallbackPrediction(request);
        }
    }

    public String assistantChat(String message, String sessionContext) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("message", message);
            body.put("context", sessionContext);

            Map<?, ?> response = aiServiceWebClient.post()
                    .uri("/api/v1/assistant/chat")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            return response != null ? String.valueOf(response.get("reply")) : fallbackAssistantReply();
        } catch (Exception ex) {
            log.warn("AI assistant service unavailable: {}", ex.getMessage());
            return fallbackAssistantReply();
        }
    }

    private AiPredictionResponse fallbackPrediction(ThreatDetectionRequest request) {
        AiPredictionResponse response = new AiPredictionResponse();
        boolean highVolume = request.getPacketCount() != null && request.getPacketCount() > 1000;
        boolean manyFailedLogins = request.getFailedLogins() != null && request.getFailedLogins() > 5;

        if (manyFailedLogins) {
            response.setThreatType("BRUTE_FORCE");
            response.setSeverity("HIGH");
            response.setConfidenceScore(0.65);
            response.setRecommendedAction("Lock affected account and block source IP");
        } else if (highVolume) {
            response.setThreatType("DDOS");
            response.setSeverity("CRITICAL");
            response.setConfidenceScore(0.60);
            response.setRecommendedAction("Rate-limit source IP and enable DDoS mitigation");
        } else {
            response.setThreatType("UNKNOWN");
            response.setSeverity("LOW");
            response.setConfidenceScore(0.30);
            response.setRecommendedAction("Continue monitoring");
        }
        response.setExplanation("Fallback heuristic used because the AI service was unreachable.");
        response.setRiskScore(response.getConfidenceScore() * 100);
        response.setReasoning("The AI service was unreachable, so this classification was produced by a simple " +
                "heuristic (failed login count / packet volume thresholds) rather than the trained model.");
        response.setContributingFactors(List.of(
                new ContributingFactor("Failed Logins", String.valueOf(request.getFailedLogins()), 0.0,
                        "Used by the fallback heuristic to distinguish brute-force from volumetric attacks."),
                new ContributingFactor("Packet Count", String.valueOf(request.getPacketCount()), 0.0,
                        "Used by the fallback heuristic to distinguish brute-force from volumetric attacks.")
        ));
        return response;
    }

    private String fallbackAssistantReply() {
        return "The AI assistant service is currently unavailable. Please try again shortly, " +
                "or consult the Threat Intelligence page for CVE and MITRE ATT&CK details.";
    }
}
