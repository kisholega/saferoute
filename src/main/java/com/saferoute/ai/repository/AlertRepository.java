package com.saferoute.ai.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.saferoute.ai.model.Alert;
import com.saferoute.ai.model.AlertStatus;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findByIdAndUserId(Long id, Long userId);

    Optional<Alert> findByShareToken(String shareToken);

    List<Alert> findTop30ByUserIdOrderByCreatedAtDesc(Long userId);

    List<Alert> findByUserIdAndStatus(Long userId, AlertStatus status);
}
