package com.saferoute.ai.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.saferoute.ai.model.AlertDelivery;

@Repository
public interface AlertDeliveryRepository extends JpaRepository<AlertDelivery, Long> {

    List<AlertDelivery> findByAlertIdOrderByIdAsc(Long alertId);

    List<AlertDelivery> findByAlertIdIn(Collection<Long> alertIds);
}
