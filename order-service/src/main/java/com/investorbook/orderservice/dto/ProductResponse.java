package com.investorbook.orderservice.dto;

import java.math.BigDecimal;

import com.investorbook.orderservice.dao.entities.ProductEntity;

public class ProductResponse {

	private Long id;
	private String name;
	private String description;
	private BigDecimal price;
	private String imageUrl;

	public ProductResponse() {
		super();
	}

	public ProductResponse(Long id, String name, String description, BigDecimal price, String imageUrl) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.price = price;
		this.imageUrl = imageUrl;
	}

	public static ProductResponse from(ProductEntity product) {
		return new ProductResponse(product.getId(), product.getName(), product.getDescription(), product.getPrice(),
				product.getImageUrl());
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public String getImageUrl() {
		return imageUrl;
	}
}
