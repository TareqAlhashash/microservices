package com.investorbook.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.investorbook.common.event.InvoiceIssued;
import com.investorbook.common.event.OrderCompleted;
import com.investorbook.common.event.PaymentFailed;
import com.investorbook.common.event.PaymentSucceeded;
import com.investorbook.orderservice.dao.OrderRepository;
import com.investorbook.orderservice.dao.entities.OrderEntity;
import com.investorbook.orderservice.dao.entities.OrderStatus;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

	@Mock
	private OrderRepository orderRepository;

	private OrderEventListener listener;

	@BeforeEach
	void setUp() {
		listener = new OrderEventListener(orderRepository);
	}

	private static OrderEntity orderWithStatus(OrderStatus status) {
		OrderEntity order = new OrderEntity("order-1", "jane@example.com", new BigDecimal("50.00"), status,
				Instant.now());
		return order;
	}

	@Test
	void onPaymentSucceeded_movesAPlacedOrderToPaid() {
		OrderEntity order = orderWithStatus(OrderStatus.PLACED);
		when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

		listener.onPaymentSucceeded(
				new PaymentSucceeded("evt-1", "order-1", "jane@example.com", new BigDecimal("50.00"), Instant.now()));

		ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);
		verify(orderRepository).save(saved.capture());
		assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.PAID);
	}

	@Test
	void onPaymentSucceeded_isANoOp_whenTheOrderIsNotCurrentlyPlaced() {
		OrderEntity order = orderWithStatus(OrderStatus.PAID);
		when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

		listener.onPaymentSucceeded(
				new PaymentSucceeded("evt-2", "order-1", "jane@example.com", new BigDecimal("50.00"), Instant.now()));

		verify(orderRepository, never()).save(any());
	}

	@Test
	void onPaymentFailed_movesAPlacedOrderToPaymentFailed() {
		OrderEntity order = orderWithStatus(OrderStatus.PLACED);
		when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

		listener.onPaymentFailed(new PaymentFailed("evt-3", "order-1", "jane@example.com", new BigDecimal("50.00"),
				"declined", Instant.now()));

		ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);
		verify(orderRepository).save(saved.capture());
		assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
	}

	@Test
	void onInvoiceIssued_movesAPaidOrderToInvoiced() {
		OrderEntity order = orderWithStatus(OrderStatus.PAID);
		when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

		listener.onInvoiceIssued(new InvoiceIssued("evt-4", "order-1", "jane@example.com", new BigDecimal("50.00"),
				"INV-0001", Instant.now()));

		ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);
		verify(orderRepository).save(saved.capture());
		assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.INVOICED);
	}

	@Test
	void onOrderCompleted_movesAnInvoicedOrderToCompleted() {
		OrderEntity order = orderWithStatus(OrderStatus.INVOICED);
		when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

		listener.onOrderCompleted(new OrderCompleted("evt-5", "order-1", "jane@example.com", Instant.now()));

		ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);
		verify(orderRepository).save(saved.capture());
		assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.COMPLETED);
	}

	@Test
	void anEventForAnUnknownOrder_isIgnored() {
		when(orderRepository.findById("missing")).thenReturn(Optional.empty());

		listener.onPaymentSucceeded(
				new PaymentSucceeded("evt-6", "missing", "jane@example.com", new BigDecimal("50.00"), Instant.now()));

		verify(orderRepository, never()).save(any());
	}
}
