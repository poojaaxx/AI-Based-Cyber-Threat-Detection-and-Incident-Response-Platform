package com.cyberguard.platform.dto.request;

import lombok.Data;

@Data
public class NotificationPreferenceRequest {
    private Boolean emailAlerts;
    private Boolean criticalAlerts;
    private Boolean dashboardAlerts;
    private Boolean weeklySummary;
}
