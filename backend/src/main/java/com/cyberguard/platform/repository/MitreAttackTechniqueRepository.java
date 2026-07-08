package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.MitreAttackTechnique;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MitreAttackTechniqueRepository extends JpaRepository<MitreAttackTechnique, Long> {
    Optional<MitreAttackTechnique> findByTechniqueId(String techniqueId);
}
