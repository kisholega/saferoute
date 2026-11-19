package com.saferoute.ai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.saferoute.ai.model.TripPoint;

@Repository
public interface TripPointRepository extends JpaRepository<TripPoint, Long> {

    List<TripPoint> findTop300ByTripIdOrderByRecordedAtDesc(Long tripId);

    List<TripPoint> findTop300ByAlertIdOrderByRecordedAtDesc(Long alertId);
}
