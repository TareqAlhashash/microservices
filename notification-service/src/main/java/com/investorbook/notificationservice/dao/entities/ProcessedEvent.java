package com.investorbook.notificationservice.dao.entities;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.springframework.data.domain.Persistable;

/**
 * Idempotency record for InvoiceIssued: this service has no other state
 * to guard on (unlike order-service's own status field), so a dedicated
 * "have I already handled this event id" table is the idempotency
 * mechanism here. The primary key's uniqueness is what actually enforces
 * it - inserting a duplicate id fails, which NotificationEventListener
 * treats as "already processed, skip" rather than an error.
 *
 * Implements Persistable so Spring Data always attempts a real INSERT
 * (isNew() always true) instead of a merge - a manually-assigned,
 * already-populated @Id would otherwise make Spring Data treat save() as
 * an update-if-exists, which would silently succeed on a duplicate instead
 * of surfacing the constraint violation this class exists to detect.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent implements Persistable<String> {

	@Id
	@Column(name = "event_id")
	private String eventId;

	@Column(name = "processed_at")
	private Instant processedAt;

	public ProcessedEvent() {
		super();
	}

	public ProcessedEvent(String eventId, Instant processedAt) {
		this.eventId = eventId;
		this.processedAt = processedAt;
	}

	@Override
	public String getId() {
		return eventId;
	}

	@Override
	public boolean isNew() {
		return true;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}
}
