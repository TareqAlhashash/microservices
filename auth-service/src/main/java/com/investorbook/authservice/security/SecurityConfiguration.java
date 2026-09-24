package com.investorbook.authservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.http.HttpServletResponse;

/**
 * This service's whole HTTP surface: /login (LoginController, authenticates and issues a JWT -
 * see JwtIssuer) and /actuator/**, kept unauthenticated for the same reason as every other
 * service in this repo (a health-check probe or metrics scraper doesn't carry this app's own
 * bearer token). No OAuth2 authorization-server endpoints - see JwtIssuer's Javadoc for why.
 */
@Configuration
public class SecurityConfiguration {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(authorize -> authorize.requestMatchers("/login", "/actuator/**").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(
						exceptions -> exceptions.authenticationEntryPoint((request, response, authenticationException) -> response
								.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	// Used directly by LoginController to authenticate the member (email + password) - the same
	// UserDetailsService/BCrypt path every version of this service has used.
	@Bean
	public DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService memberDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(memberDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return provider;
	}
}
