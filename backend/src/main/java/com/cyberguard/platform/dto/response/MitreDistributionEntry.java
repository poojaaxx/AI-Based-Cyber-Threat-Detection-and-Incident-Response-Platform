package com.cyberguard.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MitreDistributionEntry {
    private String techniqueId;
    private String name;
    private String tactic;
    private long count;
}
