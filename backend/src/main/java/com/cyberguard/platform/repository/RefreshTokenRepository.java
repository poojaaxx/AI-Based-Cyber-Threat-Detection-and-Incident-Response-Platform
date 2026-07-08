package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.RefreshToken;
import com.cyberguard.platform.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUser(User user);

    long countByRevokedFalseAndExpiryDateAfter(LocalDateTime now);
    long countByRevokedFalseAndExpiryDateBetween(LocalDateTime start, LocalDateTime end);

    /** Approximates "online users" as users holding at least one active (non-revoked, non-expired) refresh token. */
    @Query("select count(distinct rt.user.id) from RefreshToken rt where rt.revoked = false and rt.expiryDate > :now")
    long countDistinctActiveUsers(@Param("now") LocalDateTime now);
}
