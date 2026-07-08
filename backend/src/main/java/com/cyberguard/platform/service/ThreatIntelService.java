package com.cyberguard.platform.service;

import com.cyberguard.platform.entity.CveRecord;
import com.cyberguard.platform.entity.IocRecord;
import com.cyberguard.platform.entity.MitreAttackTechnique;
import com.cyberguard.platform.exception.ResourceNotFoundException;
import com.cyberguard.platform.repository.CveRecordRepository;
import com.cyberguard.platform.repository.IocRecordRepository;
import com.cyberguard.platform.repository.MitreAttackTechniqueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ThreatIntelService {

    private final CveRecordRepository cveRecordRepository;
    private final IocRecordRepository iocRecordRepository;
    private final MitreAttackTechniqueRepository mitreAttackTechniqueRepository;

    public Page<CveRecord> searchCves(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return cveRecordRepository.findAll(pageable);
        }
        return cveRecordRepository.findByTitleContainingIgnoreCaseOrCveIdContainingIgnoreCase(query, query, pageable);
    }

    public CveRecord getCveOrThrow(String cveId) {
        return cveRecordRepository.findByCveId(cveId)
                .orElseThrow(() -> new ResourceNotFoundException("CVE not found: " + cveId));
    }

    public Page<IocRecord> getActiveIocs(Pageable pageable) {
        return iocRecordRepository.findByIsActiveTrue(pageable);
    }

    public List<MitreAttackTechnique> getMitreTechniques() {
        return mitreAttackTechniqueRepository.findAll();
    }
}
