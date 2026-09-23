package com.investorbook.orderservice.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.investorbook.orderservice.dao.entities.OrderEventLogEntity;

public interface OrderEventLogRepository extends JpaRepository<OrderEventLogEntity, Long> {

	List<OrderEventLogEntity> findByOrderIdOrderByOccurredAtAsc(String orderId);
}
