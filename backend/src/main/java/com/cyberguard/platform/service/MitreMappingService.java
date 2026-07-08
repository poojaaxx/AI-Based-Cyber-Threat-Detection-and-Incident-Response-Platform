package com.cyberguard.platform.service;

import com.cyberguard.platform.entity.MitreAttackTechnique;
import com.cyberguard.platform.entity.enums.ThreatType;
import com.cyberguard.platform.repository.MitreAttackTechniqueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic ThreatType -> MITRE ATT&CK technique mapping, plus curated
 * mitigation guidance referencing MITRE's own Mitigations catalog (M-numbers).
 * Looked up on demand against the existing mitre_attack_techniques table
 * rather than requiring a new join table or backfill migration.
 */
@Service
@RequiredArgsConstructor
public class MitreMappingService {

    private final MitreAttackTechniqueRepository mitreAttackTechniqueRepository;

    private static final Map<ThreatType, List<String>> THREAT_TYPE_TO_TECHNIQUES = Map.ofEntries(
            Map.entry(ThreatType.MALWARE, List.of("T1105")),
            Map.entry(ThreatType.DDOS, List.of("T1498")),
            Map.entry(ThreatType.SQL_INJECTION, List.of("T1190")),
            Map.entry(ThreatType.XSS, List.of("T1059")),
            Map.entry(ThreatType.BRUTE_FORCE, List.of("T1110")),
            Map.entry(ThreatType.PORT_SCAN, List.of("T1595", "T1046")),
            Map.entry(ThreatType.PHISHING, List.of("T1566")),
            Map.entry(ThreatType.RANSOMWARE, List.of("T1486")),
            Map.entry(ThreatType.INSIDER_THREAT, List.of("T1078")),
            Map.entry(ThreatType.UNKNOWN, List.of())
    );

    private static final Map<String, String> MITIGATION_GUIDANCE = Map.ofEntries(
            Map.entry("T1110", "M1032 Multi-factor Authentication; M1036 Account Use Policies (lockout thresholds); M1027 Password Policies."),
            Map.entry("T1190", "M1048 Application Isolation and Sandboxing; M1030 Network Segmentation; M1051 Update Software (patch known CVEs)."),
            Map.entry("T1595", "M1056 Pre-compromise (reduce external attack surface); deploy IDS/IPS to flag reconnaissance behavior."),
            Map.entry("T1046", "M1042 Disable or Remove Feature or Program (close unused ports/services); M1030 Network Segmentation."),
            Map.entry("T1566", "M1049 Antivirus/Antimalware; M1017 User Training; M1031 Network Intrusion Prevention (email gateway filtering)."),
            Map.entry("T1486", "M1053 Data Backup (offline/immutable); M1030 Network Segmentation; M1040 Behavior Prevention on Endpoint (anti-ransomware)."),
            Map.entry("T1078", "M1026 Privileged Account Management; M1032 Multi-factor Authentication; M1047 Audit (regular access reviews)."),
            Map.entry("T1105", "M1037 Filter Network Traffic; M1031 Network Intrusion Prevention; M1049 Antivirus/Antimalware."),
            Map.entry("T1498", "M1037 Filter Network Traffic (upstream scrubbing/rate-limiting); M1030 Network Segmentation; CDN/DDoS protection services."),
            Map.entry("T1059", "M1038 Execution Prevention; M1049 Antivirus/Antimalware; input validation and output encoding at the application layer.")
    );

    public List<MitreTechniqueDetail> getTechniquesForThreatType(ThreatType threatType) {
        List<String> techniqueIds = THREAT_TYPE_TO_TECHNIQUES.getOrDefault(threatType, List.of());
        List<MitreTechniqueDetail> results = new ArrayList<>();

        for (String techniqueId : techniqueIds) {
            mitreAttackTechniqueRepository.findByTechniqueId(techniqueId).ifPresent(technique ->
                    results.add(toDetail(technique)));
        }
        return results;
    }

    private MitreTechniqueDetail toDetail(MitreAttackTechnique technique) {
        return new MitreTechniqueDetail(
                technique.getTechniqueId(),
                technique.getName(),
                technique.getTactic(),
                technique.getDescription(),
                MITIGATION_GUIDANCE.getOrDefault(technique.getTechniqueId(), "Apply standard defense-in-depth controls for this tactic.")
        );
    }

    public record MitreTechniqueDetail(String techniqueId, String name, String tactic, String description, String mitigation) {
    }
}
