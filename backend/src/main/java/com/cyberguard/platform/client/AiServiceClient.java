package com.cyberguard.platform.client;

import com.cyberguard.platform.dto.request.PolicyRecommendationRequest;
import com.cyberguard.platform.dto.request.ThreatDetectionRequest;
import com.cyberguard.platform.dto.response.AiPredictionResponse;
import com.cyberguard.platform.dto.response.ContributingFactor;
import com.cyberguard.platform.dto.response.PolicyRecommendationResponse;
import com.cyberguard.platform.dto.response.TemporalPredictionResponse;
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
                    // 30s tolerates a Render free-tier cold start (the AI service loads several
                    // .joblib model files at boot); 5s was tuned for an always-warm local dev instance.
                    .timeout(Duration.ofSeconds(30))
                    .block();
        } catch (Exception ex) {
            log.warn("AI service unavailable, using fallback heuristic classification: {}", ex.getMessage());
            return fallbackPrediction(request);
        }
    }

    /**
     * Calls the AI service's Q-learning response policy for a recommended
     * action. Unlike predictThreat()/assistantChat(), this does NOT catch
     * exceptions internally - ResponseActionService.autoRespondAdaptive()
     * needs to know the call failed so it can fall back to the static
     * autoRespond() playbook itself.
     */
    public PolicyRecommendationResponse recommendAction(String threatType, String severity, double confidenceScore) {
        return aiServiceWebClient.post()
                .uri("/api/v1/policy/recommend-action")
                .bodyValue(new PolicyRecommendationRequest(threatType, severity, confidenceScore))
                .retrieve()
                .bodyToMono(PolicyRecommendationResponse.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    /**
     * Calls the AI service's attention-LSTM temporal detector ("Model B",
     * trained on NSL-KDD) for a demo/comparison prediction. Like
     * recommendAction(), this does NOT catch exceptions internally - there is
     * no meaningful heuristic fallback for a sequence classifier, so the
     * caller (ThreatController) surfaces a 503 instead of a fabricated result.
     */
    public TemporalPredictionResponse predictTemporal(List<Map<String, Object>> records) {
        return aiServiceWebClient.post()
                .uri("/api/v1/predict/temporal")
                .bodyValue(Map.of("records", records))
                .retrieve()
                .bodyToMono(TemporalPredictionResponse.class)
                .timeout(Duration.ofSeconds(30))
                .block();
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
                    .timeout(Duration.ofSeconds(30))
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
