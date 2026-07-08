package com.cyberguard.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskScoreResponse {
    private double overallRisk;
    private String riskLevel;
    private double networkRisk;
    private double hostRisk;
    private double identityRisk;
    private double applicationRisk;
    private double dataRisk;
    private Map<String, List<String>> topContributors;
}
