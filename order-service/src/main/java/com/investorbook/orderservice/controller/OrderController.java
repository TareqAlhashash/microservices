package com.investorbook.orderservice.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.investorbook.common.util.JwtUtil;
import com.investorbook.orderservice.dao.OrderEventLogRepository;
import com.investorbook.orderservice.dao.OrderRepository;
import com.investorbook.orderservice.dao.entities.OrderEntity;
import com.investorbook.orderservice.dao.entities.OrderStatus;
import com.investorbook.orderservice.dto.OrderEventLogResponse;
import com.investorbook.orderservice.dto.OrderResponse;
import com.investorbook.orderservice.dto.PlaceOrderRequest;
import com.investorbook.orderservice.exception.OrderNotFoundException;
import com.investorbook.orderservice.service.OrderEventPublisher;

@RestController
public class OrderController {

	private final OrderRepository orderRepository;
	private final OrderEventPublisher orderEventPublisher;
	private final OrderEventLogRepository orderEventLogRepository;

	public OrderController(OrderRepository orderRepository, OrderEventPublisher orderEventPublisher,
			OrderEventLogRepository orderEventLogRepository) {
		this.orderRepository = orderRepository;
		this.orderEventPublisher = orderEventPublisher;
		this.orderEventLogRepository = orderEventLogRepository;
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

	/**
	 * The most recent orders across every customer, not just the caller's own - this backs
	 * the ops dashboard, not a "my orders" customer view, so it deliberately isn't filtered
	 * by the authenticated user the way placeOrder/getOrder's ownership implicitly is. A real
	 * deployment would gate this behind an ADMIN role; today every member is NORMAL_USER (see
	 * auth-service), so it stays behind the same MEMBER check as everything else here.
	 *
	 * search filters server-side (id contains, case-insensitive) rather than the dashboard
	 * fetching every order to filter client-side - that would defeat the point of paging as
	 * the table grows. page/size/sort bind straight onto Pageable via Spring Data's own web
	 * support (auto-configured whenever spring-boot-starter-web + a Spring Data repository are
	 * both present, as here) rather than hand-rolled @RequestParams - an oversized requested
	 * size is capped by spring.data.web.pageable.max-page-size (see application.properties),
	 * not by code here. The response is a real org.springframework.hateoas.PagedModel (spring-
	 * boot-starter-hateoas), not a hand-rolled envelope - PageImpl itself doesn't serialize
	 * cleanly with plain Jackson.
	 */
	@GetMapping("/orders")
	@PreAuthorize("hasRole('MEMBER')")
	public PagedModel<OrderResponse> listOrders(
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
			@RequestParam(required = false) String search) {
		Page<OrderEntity> orders = (search == null || search.trim().isEmpty())
				? orderRepository.findAll(pageable)
				: orderRepository.findByIdContainingIgnoreCase(search, pageable);

		Page<OrderResponse> mapped = orders.map(OrderResponse::from);
		PagedModel.PageMetadata metadata = new PagedModel.PageMetadata(mapped.getSize(), mapped.getNumber(),
				mapped.getTotalElements(), mapped.getTotalPages());
		return PagedModel.of(mapped.getContent(), metadata);
	}

	@GetMapping("/orders/{id}/events")
	@PreAuthorize("hasRole('MEMBER')")
	public List<OrderEventLogResponse> getOrderEvents(@PathVariable String id) {
		if (!orderRepository.existsById(id)) {
			throw new OrderNotFoundException(id + " is not found");
		}
		return orderEventLogRepository.findByOrderIdOrderByOccurredAtAsc(id).stream()
				.map(OrderEventLogResponse::from).collect(Collectors.toList());
	}

	private static String currentEmail() {
		return JwtUtil.getEmail(SecurityContextHolder.getContext().getAuthentication())
				.orElseThrow(() -> new IllegalStateException("authenticated request missing user_name claim"));
	}
}
