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
}
