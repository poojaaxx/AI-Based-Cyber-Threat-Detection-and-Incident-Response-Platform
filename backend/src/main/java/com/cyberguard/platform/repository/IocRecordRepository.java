package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.IocRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IocRecordRepository extends JpaRepository<IocRecord, Long> {
    Page<IocRecord> findByIsActiveTrue(Pageable pageable);
}
