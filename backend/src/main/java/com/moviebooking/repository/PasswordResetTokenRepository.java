package com.moviebooking.repository;

import com.moviebooking.entity.PasswordResetToken;
import com.moviebooking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Atomically marks one token as used. Returns 1 only for the first caller, which
     * guarantees a token can never be redeemed twice, even by concurrent requests.
     */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** Invalidates every still-unused token of a user (older links stop working). */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.user = :user and t.usedAt is null")
    int invalidateActiveTokens(@Param("user") User user, @Param("now") LocalDateTime now);
}
