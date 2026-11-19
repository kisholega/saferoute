package com.saferoute.ai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.saferoute.ai.model.RouteSearchHistory;

@Repository
public interface RouteSearchHistoryRepository extends JpaRepository<RouteSearchHistory, Long> {

    List<RouteSearchHistory> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);
}
