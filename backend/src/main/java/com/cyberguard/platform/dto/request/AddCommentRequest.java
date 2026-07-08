package com.cyberguard.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddCommentRequest {
    @NotBlank(message = "Comment cannot be empty")
    @Size(max = 2000, message = "Comment must be at most 2000 characters")
    private String comment;
}
