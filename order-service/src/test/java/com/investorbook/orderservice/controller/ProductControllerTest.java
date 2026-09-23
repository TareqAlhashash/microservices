package com.investorbook.orderservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.investorbook.orderservice.dao.ProductRepository;
import com.investorbook.orderservice.dao.entities.ProductEntity;
import com.investorbook.orderservice.dto.ProductResponse;
import com.investorbook.orderservice.exception.ProductNotFoundException;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

	@Mock
	private ProductRepository productRepository;

	private ProductController controller;

	@BeforeEach
	void setUp() {
		controller = new ProductController(productRepository);
	}

	@Test
	void listProducts_returnsEveryProductInTheCatalog() {
		ProductEntity headphones = new ProductEntity("Headphones", "Over-ear", new BigDecimal("179.99"),
				"https://example.com/headphones.jpg");
		ProductEntity kettle = new ProductEntity("Kettle", "Electric", new BigDecimal("54.30"),
				"https://example.com/kettle.jpg");
		when(productRepository.findAll()).thenReturn(Arrays.asList(headphones, kettle));

		List<ProductResponse> response = controller.listProducts();

		assertThat(response).hasSize(2);
		assertThat(response.get(0).getName()).isEqualTo("Headphones");
		assertThat(response.get(1).getName()).isEqualTo("Kettle");
	}

	@Test
	void getProduct_returnsTheProduct_whenFound() {
		ProductEntity kettle = new ProductEntity("Kettle", "Electric", new BigDecimal("54.30"),
				"https://example.com/kettle.jpg");
		when(productRepository.findById(1L)).thenReturn(Optional.of(kettle));

		ResponseEntity<ProductResponse> response = controller.getProduct(1L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().getName()).isEqualTo("Kettle");
		assertThat(response.getBody().getPrice()).isEqualByComparingTo("54.30");
	}

	@Test
	void getProduct_throwsNotFound_whenNoSuchProduct() {
		when(productRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> controller.getProduct(99L)).isInstanceOf(ProductNotFoundException.class);
	}
}
