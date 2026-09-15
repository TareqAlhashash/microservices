package com.investorbook.paymentservice.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.investorbook.paymentservice.dao.entities.ProcessedEvent;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
