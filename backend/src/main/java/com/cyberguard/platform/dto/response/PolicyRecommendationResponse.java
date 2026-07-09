package com.cyberguard.platform.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response from the AI service's tabular Q-learning response policy: the
 * recommended action plus the learned Q-value for every candidate action.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PolicyRecommendationResponse {
    private String state;
    private String recommendedAction;
    private Map<String, Double> qValues;
}
