package com.cyberguard.platform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_preferences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "email_alerts", nullable = false)
    @Builder.Default
    private Boolean emailAlerts = true;

    @Column(name = "critical_alerts", nullable = false)
    @Builder.Default
    private Boolean criticalAlerts = true;

    @Column(name = "dashboard_alerts", nullable = false)
    @Builder.Default
    private Boolean dashboardAlerts = true;

    @Column(name = "weekly_summary", nullable = false)
    @Builder.Default
    private Boolean weeklySummary = true;
}
