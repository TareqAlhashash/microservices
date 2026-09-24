package com.investorbook.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * Proves RateLimitFilter actually rejects real HTTP requests once a client is over the
 * configured limit - not just that its run() method computes the right RequestContext fields
 * (RateLimitFilterTest covers that in isolation). Runs against a public, unauthenticated route
 * (/actuator/health) so the limiter's own behaviour isn't entangled with Spring Security's.
 * Configured to 2 requests/sec so the test doesn't depend on how fast CI happens to run.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = { "eureka.client.enabled=false",
		"investorbook.security.rate-limit.requests-per-second=2" })
@AutoConfigureTestRestTemplate
class RateLimitFilterIT {

	@Autowired
	private TestRestTemplate restTemplate;

	// The auto-configured client's default Apache HttpClient5 request factory retries a 429
	// automatically once it sees this filter's own Retry-After header - correct real-client
	// behaviour, but it means the retried response (a 200, once the window refills) is what this
	// test would see instead of the immediate 429 it's trying to prove. Swapping in the plain JDK
	// request factory here disables that retry so the assertions below see the raw response.
	@BeforeEach
	void disableAutomaticRetries() {
		restTemplate.getRestTemplate().setRequestFactory(new SimpleClientHttpRequestFactory());
	}

	@Test
	void aClientOverTheLimit_getsRejectedWith429_thenRecoversOnceTheWindowRefills() throws InterruptedException {
		ResponseEntity<String> first = restTemplate.getForEntity("/actuator/health", String.class);
		ResponseEntity<String> second = restTemplate.getForEntity("/actuator/health", String.class);
		ResponseEntity<String> third = restTemplate.getForEntity("/actuator/health", String.class);

		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(third.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(third.getBody()).contains("rate limit exceeded");
		assertThat(third.getHeaders().get("Retry-After")).isNotNull();

		Thread.sleep(1100); // let the one-second window refill

		ResponseEntity<String> afterRefill = restTemplate.getForEntity("/actuator/health", String.class);
		assertThat(afterRefill.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
}
