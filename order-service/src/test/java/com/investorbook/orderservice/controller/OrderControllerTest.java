package com.investorbook.orderservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.investorbook.orderservice.dao.OrderEventLogRepository;
import com.investorbook.orderservice.dao.OrderRepository;
import com.investorbook.orderservice.dao.entities.OrderEntity;
import com.investorbook.orderservice.dao.entities.OrderEventLogEntity;
import com.investorbook.orderservice.dao.entities.OrderStatus;
import com.investorbook.orderservice.dto.OrderEventLogResponse;
import com.investorbook.orderservice.dto.OrderResponse;
import com.investorbook.orderservice.dto.PlaceOrderRequest;
import com.investorbook.orderservice.exception.OrderNotFoundException;
import com.investorbook.orderservice.service.OrderEventPublisher;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private OrderEventPublisher orderEventPublisher;

	@Mock
	private OrderEventLogRepository orderEventLogRepository;

	private OrderController controller;

	@BeforeEach
	void setUp() {
		controller = new OrderController(orderRepository, orderEventPublisher, orderEventLogRepository);
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	private static void authenticateAs(String email) {
		Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").claim("user_name", email).build();
		JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt,
				AuthorityUtils.createAuthorityList("ROLE_MEMBER"));
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

	// page/size/sort resolution and the max-page-size clamp are Spring Data Web's own job
	// (see OrderController.listOrders's Javadoc) - not something to unit-test here, since a
	// unit test constructs the Pageable directly rather than exercising the real HTTP
	// dispatch that annotation only affects. spring.data.web.pageable.max-page-size=100 is
	// verified end-to-end instead, in OrderServiceApiIT.listOrders_capsAnOversizedRequestedPageSize.

	@Test
	void listOrders_returnsAPageOfOrders_mappedFromWhateverThePageableProduces() {
		OrderEntity newer = new OrderEntity("order-2", "jane@example.com", new BigDecimal("75.00"), OrderStatus.PAID,
				Instant.now());
		OrderEntity older = new OrderEntity("order-1", "john@example.com", new BigDecimal("30.00"),
				OrderStatus.COMPLETED, Instant.now().minusSeconds(60));
		Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
		when(orderRepository.findAll(pageable)).thenReturn(new PageImpl<>(Arrays.asList(newer, older), pageable, 2));

		PagedModel<OrderResponse> response = controller.listOrders(pageable, null);

		assertThat(response.getContent()).extracting(OrderResponse::getId).containsExactly("order-2", "order-1");
		assertThat(response.getMetadata().getTotalElements()).isEqualTo(2);
		assertThat(response.getMetadata().getTotalPages()).isEqualTo(1);
		assertThat(response.getMetadata().getNumber()).isEqualTo(0);
		assertThat(response.getMetadata().getSize()).isEqualTo(20);
	}

	@Test
	void listOrders_filtersServerSide_whenSearchIsProvided() {
		OrderEntity match = new OrderEntity("abc123", "jane@example.com", new BigDecimal("50.00"), OrderStatus.PLACED,
				Instant.now());
		Pageable pageable = PageRequest.of(0, 20);
		when(orderRepository.findByIdContainingIgnoreCase(eq("abc"), eq(pageable)))
				.thenReturn(new PageImpl<>(Collections.singletonList(match), pageable, 1));

		PagedModel<OrderResponse> response = controller.listOrders(pageable, "abc");

		assertThat(response.getContent()).extracting(OrderResponse::getId).containsExactly("abc123");
		verify(orderRepository, never()).findAll(any(Pageable.class));
	}

	@Test
	void listOrders_ignoresABlankSearch_andFallsBackToFindAll() {
		Pageable pageable = PageRequest.of(0, 20);
		when(orderRepository.findAll(pageable)).thenReturn(Page.empty());

		controller.listOrders(pageable, "   ");

		verify(orderRepository).findAll(pageable);
		verify(orderRepository, never()).findByIdContainingIgnoreCase(anyString(), any());
	}

	@Test
	void getOrderEvents_returnsTheOrdersTimeline_inChronologicalOrder() {
		when(orderRepository.existsById("order-1")).thenReturn(true);
		OrderEventLogEntity placed = new OrderEventLogEntity("order-1", "OrderPlaced", "saga: order placed",
				Instant.now().minusSeconds(2));
		OrderEventLogEntity paid = new OrderEventLogEntity("order-1", "PaymentSucceeded", "saga: payment captured",
				Instant.now());
		when(orderEventLogRepository.findByOrderIdOrderByOccurredAtAsc("order-1"))
				.thenReturn(Arrays.asList(placed, paid));

		List<OrderEventLogResponse> events = controller.getOrderEvents("order-1");

		assertThat(events).hasSize(2);
		assertThat(events.get(0).getEventType()).isEqualTo("OrderPlaced");
		assertThat(events.get(1).getEventType()).isEqualTo("PaymentSucceeded");
	}

	@Test
	void getOrderEvents_throwsNotFound_whenNoSuchOrder() {
		when(orderRepository.existsById("missing")).thenReturn(false);

		assertThatThrownBy(() -> controller.getOrderEvents("missing")).isInstanceOf(OrderNotFoundException.class);
	}
}
