package com.cyberguard.platform.dto.request;

import com.cyberguard.platform.validation.ValidIpAddress;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Threat Simulation Lab input. Only sourceIp/destinationIp/port/protocol/packetSize/
 * failedLoginAttempts feed the existing (unmodified) AI prediction pipeline; the rest
 * (totalLoginAttempts, trafficType, country, userRole, deviceType, threatCategory,
 * description) are contextual only, per the approved "no model retraining" decision -
 * they are captured and displayed but never sent to the AI service.
 */
@Data
public class SimulationRequest {

    @NotBlank(message = "Source IP is required")
    @ValidIpAddress(message = "Source IP must be a valid IPv4 or IPv6 address")
    private String sourceIp;

    @NotBlank(message = "Destination IP is required")
    @ValidIpAddress(message = "Destination IP must be a valid IPv4 or IPv6 address")
    private String destinationIp;

    @Min(value = 1, message = "Port must be between 1 and 65535")
    @Max(value = 65535, message = "Port must be between 1 and 65535")
    private Integer port;

    private String protocol;

    // Represents total bytes transferred across the simulated session (not a single
    // packet's MTU-bound size) - existing Quick Scenario presets such as Ransomware/DDoS
    // intentionally use values in the hundreds of thousands to model high-volume
    // transfers, so the upper bound is set well above a single-packet limit to avoid
    // rejecting that existing, working behavior.
    @Min(value = 0, message = "Packet size cannot be negative")
    @Max(value = 100_000_000, message = "Packet size is unrealistically large")
    private Integer packetSize;

    @Min(value = 0, message = "Failed login attempts cannot be negative")
    @Max(value = 1000, message = "Failed login attempts must be at most 1000")
    private Integer failedLoginAttempts;

    // Contextual only - not used by the AI model.
    @Min(value = 0, message = "Total login attempts cannot be negative")
    @Max(value = 1000, message = "Total login attempts must be at most 1000")
    private Integer totalLoginAttempts;

    private String trafficType;
    private String country;
    private String userRole;
    private String deviceType;
    private String threatCategory;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    @AssertTrue(message = "Failed login attempts cannot exceed total login attempts")
    public boolean isLoginAttemptsConsistent() {
        if (failedLoginAttempts == null || totalLoginAttempts == null) {
            return true;
        }
        return failedLoginAttempts <= totalLoginAttempts;
    }
}
