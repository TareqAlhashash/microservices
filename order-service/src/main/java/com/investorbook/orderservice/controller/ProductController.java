package com.investorbook.orderservice.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.investorbook.orderservice.dao.ProductRepository;
import com.investorbook.orderservice.dao.entities.ProductEntity;
import com.investorbook.orderservice.dto.ProductResponse;
import com.investorbook.orderservice.exception.ProductNotFoundException;

/**
 * Deliberately unauthenticated (see SecurityConfiguration): browsing a store's catalog
 * doesn't require being logged in, unlike placing an order.
 */
@RestController
public class ProductController {

	private final ProductRepository productRepository;

	public ProductController(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	@GetMapping("/products")
	public List<ProductResponse> listProducts() {
		return productRepository.findAll().stream().map(ProductResponse::from).collect(Collectors.toList());
	}

	@GetMapping("/products/{id}")
	public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
		ProductEntity product = productRepository.findById(id)
				.orElseThrow(() -> new ProductNotFoundException(id + " is not found"));
		return ResponseEntity.ok(ProductResponse.from(product));
	}
}
