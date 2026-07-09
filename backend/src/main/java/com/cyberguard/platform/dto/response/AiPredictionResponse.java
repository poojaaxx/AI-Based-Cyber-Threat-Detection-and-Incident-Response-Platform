package com.cyberguard.platform.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiPredictionResponse {
    private String threatType;
    private String severity;
    private Double confidenceScore;
    private String recommendedAction;
    private String explanation;
    private Double riskScore;
    private String reasoning;
    private List<ContributingFactor> contributingFactors = new ArrayList<>();
    /** Per-instance SHAP explanation (Phase 3 addition to the AI service's /predict response).
     * Optional/additive - defaults to empty for any AI service response that doesn't send it. */
    private List<ContributingFactor> shapExplanation = new ArrayList<>();
}
