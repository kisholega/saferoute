package com.saferoute.ai.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.saferoute.ai.model.Trip;
import com.saferoute.ai.model.TripStatus;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    Optional<Trip> findByIdAndUserId(Long id, Long userId);

    Optional<Trip> findByShareToken(String shareToken);

    List<Trip> findByStatusIn(Collection<TripStatus> statuses);

    List<Trip> findByUserIdAndStatusIn(Long userId, Collection<TripStatus> statuses);

    List<Trip> findTop10ByUserIdOrderByStartedAtDesc(Long userId);
}
