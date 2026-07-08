package com.cyberguard.platform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mitre_attack_techniques")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MitreAttackTechnique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "technique_id", nullable = false, unique = true, length = 20)
    private String techniqueId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 100)
    private String tactic;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;
}
