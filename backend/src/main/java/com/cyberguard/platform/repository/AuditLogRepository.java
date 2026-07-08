package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    // left join fetch eagerly loads the (nullable, @ManyToOne) user so it is not
    // returned as null by Jackson once the Hibernate session closes (open-in-view
    // is disabled). Safe to paginate: fetching a single-valued association does not
    // multiply the row count the way a collection fetch join would.
    @Query(value = "select a from AuditLog a left join fetch a.user order by a.createdAt desc",
           countQuery = "select count(a) from AuditLog a")
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select a from AuditLog a left join fetch a.user u join u.roles r where r.name = :roleName order by a.createdAt desc")
    List<AuditLog> findRecentByUserRole(@Param("roleName") String roleName, Pageable pageable);

    long countByCreatedAtAfter(LocalDateTime since);

    @Query("select a.action, count(a) from AuditLog a where a.createdAt > :since group by a.action")
    List<Object[]> countGroupedByActionSince(@Param("since") LocalDateTime since);
}
