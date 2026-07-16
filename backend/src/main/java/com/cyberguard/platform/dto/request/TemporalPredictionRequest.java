package com.cyberguard.platform.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Sent to POST /api/v1/threats/predict-temporal, which proxies to the AI
 * service's POST /api/v1/predict/temporal. Each record's keys must match
 * ai-service/app/schemas/temporal.py's KddConnectionRecord field names
 * (e.g. protocol_type, src_bytes, serror_rate); kept as a raw map here rather
 * than a 34-field typed class since the backend only passes records through,
 * it never reads or validates individual fields itself.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemporalPredictionRequest {
    private List<Map<String, Object>> records;
}
