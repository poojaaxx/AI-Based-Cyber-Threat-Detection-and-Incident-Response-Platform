package com.cyberguard.platform.dto.request;

import com.cyberguard.platform.entity.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 120, message = "Full name must be at most 120 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 150, message = "Email must be at most 150 characters")
    private String email;

    @Size(max = 100, message = "Department must be at most 100 characters")
    private String department;

    @Size(max = 30, message = "Phone must be at most 30 characters")
    private String phone;

    private UserStatus status;

    /** ROLE_ADMIN / ROLE_ANALYST / ROLE_USER */
    @NotBlank(message = "Role is required")
    private String role;
}
