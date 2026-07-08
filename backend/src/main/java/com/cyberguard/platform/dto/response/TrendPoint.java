package com.cyberguard.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Generic (label, count) time-series point, used for weekly/monthly threat trends. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrendPoint {
    private String label;
    private long count;
}
