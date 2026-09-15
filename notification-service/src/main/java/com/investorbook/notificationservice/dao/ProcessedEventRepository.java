package com.investorbook.notificationservice.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.investorbook.notificationservice.dao.entities.ProcessedEvent;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
