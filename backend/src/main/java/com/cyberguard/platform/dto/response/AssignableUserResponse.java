package com.cyberguard.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal user projection for the Incident "Assign Analyst" dropdown - deliberately
 * excludes everything except what the dropdown needs (no status/roles/contact info).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignableUserResponse {
    private Long id;
    private String username;
    private String fullName;
}
