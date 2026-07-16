package com.cyberguard.platform.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response from the AI service's attention-LSTM temporal detector ("Model B",
 * POST /api/v1/predict/temporal). Mirrors ai-service/app/schemas/temporal.py's
 * TemporalPredictionResponse - a separate 5-class (Normal/DoS/Probe/R2L/U2R)
 * taxonomy from the primary /predict RandomForest model, not interchangeable
 * with AiPredictionResponse.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TemporalPredictionResponse {
    private String threatCategory;
    private Double confidenceScore;
    private Map<String, Double> classProbabilities;
    private List<Double> attentionWeights;
    private String modelVersion;
    private String note;
    private List<Map<String, Double>> attentionExplanation;
}
