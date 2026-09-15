package com.investorbook.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;

import com.investorbook.orderservice.dao.OrderRepository;
import com.investorbook.orderservice.dao.entities.OrderEntity;
import com.investorbook.orderservice.dao.entities.OrderStatus;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private OrderEventPublisher orderEventPublisher;

	private OrderController controller;

	@BeforeEach
	void setUp() {
		controller = new OrderController(orderRepository, orderEventPublisher);
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	private static void authenticateAs(String email) {
		OAuth2AuthenticationDetails details = new OAuth2AuthenticationDetails(new MockHttpServletRequest());
		details.setDecodedDetails(Collections.singletonMap("user_name", email));
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(email, null,
				Collections.emptyList());
		authentication.setDetails(details);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	@Test
	void placeOrder_persistsAsPlaced_andPublishesOrderPlaced_usingTheAuthenticatedEmail() {
		authenticateAs("jane@example.com");
		PlaceOrderRequest request = new PlaceOrderRequest(new BigDecimal("50.00"));

		ResponseEntity<OrderResponse> response = controller.placeOrder(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody().getStatus()).isEqualTo("PLACED");

		ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);
		verify(orderRepository).save(saved.capture());
		assertThat(saved.getValue().getCustomerEmail()).isEqualTo("jane@example.com");
		assertThat(saved.getValue().getAmount()).isEqualByComparingTo("50.00");

		verify(orderEventPublisher).publishOrderPlaced(saved.getValue());
	}

	@Test
	void getOrder_returnsTheCurrentStatus_whenFound() {
		OrderEntity order = new OrderEntity("order-1", "jane@example.com", new BigDecimal("50.00"), OrderStatus.PAID,
				Instant.now());
		when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

		ResponseEntity<OrderResponse> response = controller.getOrder("order-1");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().getStatus()).isEqualTo("PAID");
	}

	@Test
	void getOrder_throwsNotFound_whenNoSuchOrder() {
		when(orderRepository.findById("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> controller.getOrder("missing")).isInstanceOf(OrderNotFoundException.class);
	}
}
