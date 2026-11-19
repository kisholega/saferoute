package com.saferoute.ai.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.saferoute.ai.model.RememberToken;

@Repository
public interface RememberTokenRepository extends JpaRepository<RememberToken, Long> {

    Optional<RememberToken> findByTokenHash(String tokenHash);

    @Transactional
    void deleteByTokenHash(String tokenHash);

    @Transactional
    @Modifying
    @Query("delete from RememberToken t where t.expiresAt < :now")
    int deleteExpired(Instant now);
}
