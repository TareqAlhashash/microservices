package com.investorbook.apigateway.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;

@Configuration
@EnableResourceServer
@EnableWebSecurity
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

	@Override
	public void configure(WebSecurity web) throws Exception {
		// /actuator/**: see member-service's SecurityConfiguration for why this is unauthenticated.
		// /order-service/products/**: proxied through to order-service's own public catalog
		// endpoints (see its SecurityConfiguration) - browsing the storefront doesn't require a token.
		// /dashboard/**: this app's own DashboardController, a health-check aggregator - same
		// "no bearer token" exemption as /actuator/** itself (see its Javadoc).
		web.ignoring().antMatchers("/login", "/uaa/oauth/token", "/member-service/signup", "/actuator/**",
				"/order-service/products", "/order-service/products/**", "/dashboard/**");
	}
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		// CORS is handled by a standalone CorsFilter (see CorsConfig), not here - see its
		// Javadoc for why: WebSecurity.ignoring() above would otherwise skip it entirely.
		http.authorizeRequests().anyRequest().authenticated()
		.and().csrf().disable()
		.logout().logoutUrl("/logout").permitAll()
		.logoutSuccessUrl("/")
		.and()
		.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
	}
}
