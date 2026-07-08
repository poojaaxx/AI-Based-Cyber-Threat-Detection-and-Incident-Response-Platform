package com.cyberguard.platform.dto.response;

import com.cyberguard.platform.entity.Threat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThreatInvestigationResponse {
    private Threat threat;
    private ThreatExplanationResponse explanation;
    private IncidentSummary incident;
    private List<InvestigationEvent> timeline;
}
