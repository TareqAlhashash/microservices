package com.investorbook.orderservice.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.investorbook.orderservice.dao.entities.ProductEntity;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
}
