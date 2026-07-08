package com.cyberguard.platform.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 120, message = "Full name must be at most 120 characters")
    private String fullName;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 60, message = "Username must be between 3 and 60 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 150, message = "Email must be at most 150 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;

    @NotBlank(message = "Please confirm the password")
    private String confirmPassword;

    @Size(max = 100, message = "Department must be at most 100 characters")
    private String department;

    @Size(max = 30, message = "Phone must be at most 30 characters")
    private String phone;

    /** ROLE_ADMIN / ROLE_ANALYST / ROLE_USER */
    @NotBlank(message = "Role is required")
    private String role;
}
