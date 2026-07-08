package com.cyberguard.platform.controller;

import com.cyberguard.platform.entity.CveRecord;
import com.cyberguard.platform.entity.IocRecord;
import com.cyberguard.platform.entity.MitreAttackTechnique;
import com.cyberguard.platform.service.ThreatIntelService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/threat-intel")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
@Tag(name = "Threat Intelligence", description = "CVE database, IOC feed and MITRE ATT&CK technique mapping")
public class ThreatIntelController {

    private final ThreatIntelService threatIntelService;

    @GetMapping("/cve")
    public ResponseEntity<Page<CveRecord>> searchCves(@RequestParam(required = false) String query,
                                                         @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(threatIntelService.searchCves(query, pageable));
    }

    @GetMapping("/cve/{cveId}")
    public ResponseEntity<CveRecord> getCve(@PathVariable String cveId) {
        return ResponseEntity.ok(threatIntelService.getCveOrThrow(cveId));
    }

    @GetMapping("/ioc")
    public ResponseEntity<Page<IocRecord>> getIocs(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(threatIntelService.getActiveIocs(pageable));
    }

    @GetMapping("/mitre")
    public ResponseEntity<List<MitreAttackTechnique>> getMitreTechniques() {
        return ResponseEntity.ok(threatIntelService.getMitreTechniques());
    }
}
