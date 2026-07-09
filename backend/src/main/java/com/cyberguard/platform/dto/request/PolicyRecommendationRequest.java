package com.cyberguard.platform.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sent to the AI service's POST /api/v1/policy/recommend-action to get an
 * RL-policy-recommended response action for an already-classified threat.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyRecommendationRequest {
    private String threatType;
    private String severity;
    private Double confidenceScore;
}
