package com.cyberguard.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Generic (label, count) pair reused across several analytics breakdowns (top sources, top assets, most active analysts). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountEntry {
    private String label;
    private long count;
}
