package com.saferoute.ai.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.saferoute.ai.model.IncidentReport;

@Repository
public interface IncidentReportRepository extends JpaRepository<IncidentReport, Long> {

    @Query("select r from IncidentReport r where r.latitude between :south and :north "
            + "and r.longitude between :west and :east and r.createdAt >= :since order by r.createdAt desc")
    List<IncidentReport> findInBox(double south, double west, double north, double east, LocalDateTime since);

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);
}
