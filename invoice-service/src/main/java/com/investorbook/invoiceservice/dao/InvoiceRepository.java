package com.investorbook.invoiceservice.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.investorbook.invoiceservice.dao.entities.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {

	Optional<Invoice> findByOrderId(String orderId);
}
