package com.cyberguard.platform.dto.response;

import com.cyberguard.platform.entity.Threat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThreatDetectionResponse {
    private Threat threat;
    private boolean incidentCreated;
    private Long incidentId;
    private String incidentNumber;
}
