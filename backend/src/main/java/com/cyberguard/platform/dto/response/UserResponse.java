package com.cyberguard.platform.dto.response;

import com.cyberguard.platform.entity.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private Long id;
    private String fullName;
    private String username;
    private String email;
    private String department;
    private String phone;
    private UserStatus status;
    private List<String> roles;
    private LocalDateTime lastLoginAt;
    private Integer failedLoginAttempts;
    private Boolean mustChangePassword;
    private LocalDateTime createdAt;
}
