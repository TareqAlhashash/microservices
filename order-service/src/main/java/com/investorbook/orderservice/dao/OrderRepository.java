package com.investorbook.orderservice.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.investorbook.orderservice.dao.entities.OrderEntity;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {
}
