package com.cyberguard.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemHealthInfo {
    private String backendStatus;
    private String databaseStatus;
    private String aiServiceStatus;
    private long uptimeSeconds;
}
