package com.investorbook.orderservice.service;

import java.time.Instant;
import java.util.UUID;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.investorbook.common.util.JwtUtil;
import com.investorbook.orderservice.dao.OrderRepository;
import com.investorbook.orderservice.dao.entities.OrderEntity;
import com.investorbook.orderservice.dao.entities.OrderStatus;

@RestController
public class OrderController {

	private final OrderRepository orderRepository;
	private final OrderEventPublisher orderEventPublisher;

	public OrderController(OrderRepository orderRepository, OrderEventPublisher orderEventPublisher) {
		this.orderRepository = orderRepository;
		this.orderEventPublisher = orderEventPublisher;
	}

	/**
	 * customerEmail comes from the authenticated JWT, never the request body -
	 * a client can only ever place an order as themselves.
	 */
	@PostMapping("/orders")
	@PreAuthorize("hasRole('MEMBER')")
	public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
		OrderEntity order = new OrderEntity(UUID.randomUUID().toString(), currentEmail(), request.getAmount(),
				OrderStatus.PLACED, Instant.now());
		orderRepository.save(order);

		orderEventPublisher.publishOrderPlaced(order);

		return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
	}

	@GetMapping("/orders/{id}")
	@PreAuthorize("hasRole('MEMBER')")
	public ResponseEntity<OrderResponse> getOrder(@PathVariable String id) {
		OrderEntity order = orderRepository.findById(id)
				.orElseThrow(() -> new OrderNotFoundException(id + " is not found"));
		return ResponseEntity.ok(OrderResponse.from(order));
	}

	private static String currentEmail() {
		return JwtUtil.getEmail(SecurityContextHolder.getContext().getAuthentication())
				.orElseThrow(() -> new IllegalStateException("authenticated request missing user_name claim"));
	}
}
