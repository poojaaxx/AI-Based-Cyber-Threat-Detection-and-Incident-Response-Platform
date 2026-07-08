package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.User;
import com.cyberguard.platform.entity.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsernameOrEmail(String username, String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    long countByRoles_Name(String roleName);

    @Query("select distinct u from User u join u.roles r where r.name in (:roleNames) and u.status = :status order by u.fullName")
    List<User> findByRoles_NameInAndStatus(@Param("roleNames") List<String> roleNames, @Param("status") UserStatus status);

    @Query("select distinct u from User u left join u.roles r where " +
            "(:search is null or lower(u.username) like :search or lower(u.fullName) like :search or lower(u.email) like :search) " +
            "and (:status is null or u.status = :status) " +
            "and (:department is null or u.department = :department) " +
            "and (:role is null or r.name = :role)")
    Page<User> search(@Param("search") String search,
                       @Param("status") UserStatus status,
                       @Param("department") String department,
                       @Param("role") String role,
                       Pageable pageable);
}
