package com.investorbook.invoiceservice.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.investorbook.invoiceservice.dao.entities.ProcessedEvent;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
