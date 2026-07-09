package com.cyberguard.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThreatExplanationResponse {
    private Long threatId;
    private String threatType;
    private String severity;
    private BigDecimal confidenceScore;
    private BigDecimal riskScore;
    private String reasoning;
    private String recommendedAction;
    private List<ContributingFactor> contributingFactors;
    /** Per-instance SHAP explanation (Phase 3 addition) - empty for threats detected before this
     * field existed, or if the AI service didn't return one. Additive; contributingFactors above
     * is unchanged and still always populated the same way it always was. */
    private List<ContributingFactor> shapExplanation;
    private List<MitreTechniqueInfo> mitreTechniques;
    private List<String> recommendations;
}
