package com.cyberguard.platform.dto.request;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String fullName;
    private String department;
    private String phone;
}
