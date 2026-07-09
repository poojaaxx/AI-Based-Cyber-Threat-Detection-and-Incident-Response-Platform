package com.cyberguard.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Current on/off state of ResponseActionService's adaptive-mode-enabled flag. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdaptiveModeResponse {
    private boolean enabled;
}
