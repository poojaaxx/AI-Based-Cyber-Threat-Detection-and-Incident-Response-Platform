package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.SimulationRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SimulationRunRepository extends JpaRepository<SimulationRun, Long> {

    @Query("select s from SimulationRun s left join fetch s.resultThreat left join fetch s.performedBy where s.id = :id")
    Optional<SimulationRun> findByIdFetched(@Param("id") Long id);

    @Query(value = "select s from SimulationRun s left join fetch s.resultThreat left join fetch s.performedBy where " +
            "(:search is null or lower(s.sourceIp) like :search or lower(s.destinationIp) like :search " +
            "  or lower(s.description) like :search) " +
            "and (:threatCategory is null or s.threatCategory = :threatCategory) " +
            "and (:trafficType is null or s.trafficType = :trafficType)",
            countQuery = "select count(s) from SimulationRun s where " +
            "(:search is null or lower(s.sourceIp) like :search or lower(s.destinationIp) like :search " +
            "  or lower(s.description) like :search) " +
            "and (:threatCategory is null or s.threatCategory = :threatCategory) " +
            "and (:trafficType is null or s.trafficType = :trafficType)")
    Page<SimulationRun> search(@Param("search") String search,
                                @Param("threatCategory") String threatCategory,
                                @Param("trafficType") String trafficType,
                                Pageable pageable);
}
