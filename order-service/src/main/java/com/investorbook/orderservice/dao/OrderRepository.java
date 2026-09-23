package com.investorbook.orderservice.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.investorbook.orderservice.dao.entities.OrderEntity;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {

	// findAll(Pageable) is already inherited from JpaRepository - this one exists only for the
	// dashboard's order-id search box, filtering server-side rather than fetching every order
	// into memory to filter there.
	Page<OrderEntity> findByIdContainingIgnoreCase(String idFragment, Pageable pageable);
}
