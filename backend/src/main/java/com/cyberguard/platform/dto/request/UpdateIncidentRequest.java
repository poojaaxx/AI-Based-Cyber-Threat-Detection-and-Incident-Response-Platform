package com.cyberguard.platform.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateIncidentRequest {
    private String status;
    private Long assignedTo;

    @Size(max = 5000, message = "Resolution notes must be at most 5000 characters")
    private String resolutionNotes;
}
