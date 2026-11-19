package com.saferoute.ai.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.saferoute.ai.model.EmergencyContact;

@Repository
public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {

    /** Primary contact first, then in the order they were added. */
    List<EmergencyContact> findByUserIdOrderByIsPrimaryDescCreatedAtAsc(Long userId);

    Optional<EmergencyContact> findByIdAndUserId(Long id, Long userId);
}
