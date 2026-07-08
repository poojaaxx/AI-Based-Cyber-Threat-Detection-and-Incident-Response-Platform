package com.cyberguard.platform.dto.response;

import com.cyberguard.platform.entity.Threat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationResponse {
    private Long simulationId;
    private Threat threat;
    private ThreatExplanationResponse explanation;
    private List<String> recommendations;
    private boolean incidentCreated;
    private Long incidentId;
    private String incidentNumber;
    private LocalDateTime predictionTime;

    // Contextual fields echoed back for display (not used by the AI model).
    private String country;
    private String userRole;
    private String deviceType;
    private String trafficType;
    private String threatCategory;
    private String description;
    private Integer totalLoginAttempts;
    private Integer packetSize;
}
