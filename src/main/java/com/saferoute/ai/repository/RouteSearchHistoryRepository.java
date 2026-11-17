package com.saferoute.ai.repository;

import com.saferoute.ai.model.RouteSearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteSearchHistoryRepository extends JpaRepository<RouteSearchHistory, Long> {
    List<RouteSearchHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}
