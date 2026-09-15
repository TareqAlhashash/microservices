package com.investorbook.common.event;

/**
 * One topic per event type, partitioned by order id (see each producer's
 * KafkaTemplate.send(topic, orderId, event) call) so all events for a given
 * order keep their relative order within a partition - not global ordering
 * across orders, which this saga doesn't need.
 */
public final class Topics {

	public static final String ORDER_PLACED = "order.placed";
	public static final String PAYMENT_SUCCEEDED = "payment.succeeded";
	public static final String PAYMENT_FAILED = "payment.failed";
	public static final String INVOICE_ISSUED = "invoice.issued";
	public static final String ORDER_COMPLETED = "order.completed";

	private Topics() {
	}
}
