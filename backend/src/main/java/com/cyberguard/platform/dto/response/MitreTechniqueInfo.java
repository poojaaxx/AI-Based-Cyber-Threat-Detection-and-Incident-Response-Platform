package com.cyberguard.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MitreTechniqueInfo {
    private String techniqueId;
    private String name;
    private String tactic;
    private String description;
    private String mitigation;
}
