package com.cyberguard.platform.repository;
import com.cyberguard.platform.entity.CollectorState;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface CollectorStateRepository extends JpaRepository<CollectorState, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CollectorState c where c.collectorId = :id")
    Optional<CollectorState> lockById(String id);
}
