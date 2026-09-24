package com.investorbook.orderservice.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.investorbook.orderservice.dao.ProductRepository;
import com.investorbook.orderservice.dao.entities.ProductEntity;

/**
 * Seeds a demo product catalog on first startup only (skipped once the table has rows),
 * so restarting the service doesn't keep re-inserting or duplicating products. The
 * items and prices are a fixed, curated list rather than randomly generated each run -
 * that keeps the storefront's contents stable across restarts and deterministic for tests.
 */
@Component
public class ProductCatalogSeeder implements ApplicationRunner {

	private static final List<ProductEntity> CATALOG = Arrays.asList(
			new ProductEntity("Wireless Noise-Cancelling Headphones",
					"Over-ear Bluetooth headphones with active noise cancellation and 30-hour battery life",
					new BigDecimal("179.99"), "https://picsum.photos/seed/headphones/400/400"),
			new ProductEntity("Mechanical Keyboard",
					"Compact 75% mechanical keyboard with hot-swappable switches and RGB backlighting",
					new BigDecimal("94.50"), "https://picsum.photos/seed/keyboard/400/400"),
			new ProductEntity("Espresso Machine",
					"15-bar pump espresso machine with built-in milk frother", new BigDecimal("249.00"),
					"https://picsum.photos/seed/espresso/400/400"),
			new ProductEntity("Trail Running Shoes",
					"Lightweight trail running shoes with a grippy lugged outsole", new BigDecimal("112.25"),
					"https://picsum.photos/seed/shoes/400/400"),
			new ProductEntity("Stainless Steel Water Bottle",
					"Insulated 750ml bottle that keeps drinks cold for 24 hours", new BigDecimal("28.99"),
					"https://picsum.photos/seed/bottle/400/400"),
			new ProductEntity("4K Action Camera",
					"Waterproof action camera with image stabilization and a 4K/60fps sensor",
					new BigDecimal("329.99"), "https://picsum.photos/seed/camera/400/400"),
			new ProductEntity("Ceramic Cookware Set",
					"10-piece non-stick ceramic cookware set, oven safe to 450°F", new BigDecimal("189.00"),
					"https://picsum.photos/seed/cookware/400/400"),
			new ProductEntity("Standing Desk Converter",
					"Adjustable-height desktop riser for switching between sitting and standing",
					new BigDecimal("143.75"), "https://picsum.photos/seed/desk/400/400"),
			new ProductEntity("Leather Backpack",
					"Full-grain leather backpack with a padded 15-inch laptop sleeve", new BigDecimal("158.40"),
					"https://picsum.photos/seed/backpack/400/400"),
			new ProductEntity("Smart LED Light Strip",
					"16-million-color WiFi LED strip with app and voice control", new BigDecimal("34.99"),
					"https://picsum.photos/seed/lights/400/400"),
			new ProductEntity("Cast Iron Skillet",
					"Pre-seasoned 12-inch cast iron skillet for stovetop, oven, or campfire", new BigDecimal("42.00"),
					"https://picsum.photos/seed/skillet/400/400"),
			new ProductEntity("Portable Bluetooth Speaker",
					"Rugged, waterproof speaker with 20 hours of playtime", new BigDecimal("67.99"),
					"https://picsum.photos/seed/speaker/400/400"),
			new ProductEntity("Weighted Blanket",
					"15lb weighted blanket with a breathable cotton cover", new BigDecimal("79.95"),
					"https://picsum.photos/seed/blanket/400/400"),
			new ProductEntity("Electric Kettle",
					"1.7L variable-temperature electric kettle with a keep-warm setting", new BigDecimal("54.30"),
					"https://picsum.photos/seed/kettle/400/400"));

	private final ProductRepository productRepository;

	public ProductCatalogSeeder(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (productRepository.count() == 0) {
			productRepository.saveAll(CATALOG);
		}
	}
}
