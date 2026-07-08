package com.cyberguard.platform.service;

import com.cyberguard.platform.dto.request.SimulationRequest;
import com.cyberguard.platform.dto.request.ThreatDetectionRequest;
import com.cyberguard.platform.dto.response.SimulationResponse;
import com.cyberguard.platform.dto.response.ThreatDetectionResponse;
import com.cyberguard.platform.dto.response.ThreatExplanationResponse;
import com.cyberguard.platform.entity.SimulationRun;
import com.cyberguard.platform.entity.Threat;
import com.cyberguard.platform.entity.User;
import com.cyberguard.platform.exception.ResourceNotFoundException;
import com.cyberguard.platform.repository.SimulationRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

/**
 * Threat Simulation Lab orchestration. Reuses the existing (unmodified) AI
 * detection pipeline via ThreatService.detectAndPersist and the existing
 * Explainable AI + MITRE output via ThreatInvestigationService.explain -
 * nothing about threat classification is duplicated or retrained here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationService {

    private final ThreatService threatService;
    private final ThreatInvestigationService threatInvestigationService;
    private final SimulationRunRepository simulationRunRepository;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    // Typical average payload size per packet for common protocols - used to derive a
    // realistic packetCount from the form's single "Packet Size" (total bytes) field.
    private static final Map<String, Integer> AVG_BYTES_PER_PACKET = Map.ofEntries(
            Map.entry("UDP", 100), Map.entry("TCP", 300), Map.entry("HTTP", 500), Map.entry("HTTPS", 500),
            Map.entry("SMTP", 300), Map.entry("SMB", 115), Map.entry("ICMP", 64), Map.entry("FTP", 500),
            Map.entry("SSH", 50), Map.entry("RDP", 300)
    );

    // Typical packets/second for the same protocols - used with packetCount to derive a
    // realistic session duration (e.g. a UDP flood is high-volume AND high-rate/short,
    // while a brute-force session is low-volume but slow/throttled).
    private static final Map<String, Double> PROTOCOL_PACKET_RATE = Map.ofEntries(
            Map.entry("UDP", 6000.0), Map.entry("TCP", 60.0), Map.entry("HTTP", 60.0), Map.entry("HTTPS", 60.0),
            Map.entry("SMTP", 30.0), Map.entry("SMB", 530.0), Map.entry("ICMP", 60.0), Map.entry("FTP", 60.0),
            Map.entry("SSH", 12.0), Map.entry("RDP", 60.0)
    );

    @Transactional
    public SimulationResponse runSimulation(SimulationRequest request, User actor) {
        ThreatDetectionResponse detection = threatService.detectAndPersist(buildDetectionRequest(request), actor);
        Threat threat = detection.getThreat();

        ThreatExplanationResponse explanation = threatInvestigationService.explain(threat.getId());
        List<String> recommendations = explanation.getRecommendations();

        SimulationRun run = SimulationRun.builder()
                .performedBy(actor)
                .sourceIp(request.getSourceIp())
                .destinationIp(request.getDestinationIp())
                .port(request.getPort())
                .protocol(request.getProtocol())
                .packetSize(request.getPacketSize())
                .failedLoginAttempts(request.getFailedLoginAttempts())
                .totalLoginAttempts(request.getTotalLoginAttempts())
                .trafficType(request.getTrafficType())
                .country(request.getCountry())
                .userRole(request.getUserRole())
                .deviceType(request.getDeviceType())
                .threatCategory(request.getThreatCategory())
                .description(request.getDescription())
                .resultThreat(threat)
                .recommendations(serialize(recommendations))
                .build();
        run = simulationRunRepository.save(run);

        return SimulationResponse.builder()
                .simulationId(run.getId())
                .threat(threat)
                .explanation(explanation)
                .recommendations(recommendations)
                .incidentCreated(detection.isIncidentCreated())
                .incidentId(detection.getIncidentId())
                .incidentNumber(detection.getIncidentNumber())
                .predictionTime(run.getCreatedAt())
                .country(request.getCountry())
                .userRole(request.getUserRole())
                .deviceType(request.getDeviceType())
                .trafficType(request.getTrafficType())
                .threatCategory(request.getThreatCategory())
                .description(request.getDescription())
                .totalLoginAttempts(request.getTotalLoginAttempts())
                .packetSize(request.getPacketSize())
                .build();
    }

    /**
     * Maps the simplified Simulation Lab form onto the existing 8-feature model input.
     * "Port" is used as destinationPort (the security-relevant side, e.g. 22/445/3389);
     * sourcePort is a random ephemeral port since the model needs a value but the
     * simplified form doesn't collect one. "Packet Size" is read as total bytes
     * transferred; packetCount and durationMs are DERIVED from packetSize/protocol/
     * failedLoginAttempts using realistic per-protocol packet-size and packet-rate
     * assumptions (see AVG_BYTES_PER_PACKET / PROTOCOL_PACKET_RATE) rather than fixed
     * constants - flat constants were found to produce feature combinations far outside
     * the model's training distribution (e.g. a large SMB transfer with only 50 packets
     * over 2s reads nothing like real ransomware traffic and gets misclassified). None
     * of this touches the Random Forest model itself - only how form inputs are mapped
     * onto its existing feature set, and never uses the contextual-only fields
     * (country/userRole/deviceType/trafficType/threatCategory) to influence the mapping.
     */
    private ThreatDetectionRequest buildDetectionRequest(SimulationRequest request) {
        String protocol = request.getProtocol() != null ? request.getProtocol().toUpperCase() : "TCP";
        long packetSize = request.getPacketSize() != null ? request.getPacketSize() : 0L;
        Integer failedLogins = request.getFailedLoginAttempts();

        int avgBytesPerPacket = AVG_BYTES_PER_PACKET.getOrDefault(protocol, 300);
        int packetCount = packetSize > 0 ? Math.max(1, (int) (packetSize / avgBytesPerPacket)) : 1;

        int durationMs;
        if (failedLogins != null && failedLogins > 5) {
            // Throttled/interactive login attempts dominate the session length for auth attacks.
            durationMs = Math.max(500, failedLogins * 450);
        } else {
            double packetsPerSecond = PROTOCOL_PACKET_RATE.getOrDefault(protocol, 60.0);
            durationMs = Math.max(150, (int) Math.round(packetCount / packetsPerSecond * 1000));
        }

        ThreatDetectionRequest dr = new ThreatDetectionRequest();
        dr.setSourceIp(request.getSourceIp());
        dr.setDestinationIp(request.getDestinationIp());
        dr.setDestinationPort(request.getPort());
        dr.setSourcePort(1024 + secureRandom.nextInt(64511));
        dr.setProtocol(request.getProtocol());
        dr.setBytesTransferred(packetSize);
        dr.setPacketCount(packetCount);
        dr.setDurationMs(durationMs);
        dr.setFailedLogins(failedLogins != null ? failedLogins : 0);
        dr.setFlagged(failedLogins != null && failedLogins > 0);
        return dr;
    }

    public Page<SimulationRun> search(String search, String threatCategory, String trafficType, Pageable pageable) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : "%" + search.trim().toLowerCase() + "%";
        String normalizedCategory = (threatCategory == null || threatCategory.isBlank()) ? null : threatCategory;
        String normalizedTraffic = (trafficType == null || trafficType.isBlank()) ? null : trafficType;
        return simulationRunRepository.search(normalizedSearch, normalizedCategory, normalizedTraffic, pageable);
    }

    public SimulationRun getOrThrow(Long id) {
        return simulationRunRepository.findByIdFetched(id)
                .orElseThrow(() -> new ResourceNotFoundException("Simulation run not found with id: " + id));
    }

    public void delete(Long id) {
        if (!simulationRunRepository.existsById(id)) {
            throw new ResourceNotFoundException("Simulation run not found with id: " + id);
        }
        simulationRunRepository.deleteById(id);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize simulation recommendations: {}", e.getMessage());
            return "[]";
        }
    }
}
