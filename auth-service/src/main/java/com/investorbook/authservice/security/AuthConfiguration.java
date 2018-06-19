package com.investorbook.authservice.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;

import com.investorbook.authservice.config.JwtAuthenticationConfig;

@Configuration
@EnableAuthorizationServer
public class AuthConfiguration extends AuthorizationServerConfigurerAdapter {

	@Autowired 
	JwtAuthenticationConfig config;
	
	@Bean
	public JwtAuthenticationConfig jwtConfig() {
	    return new JwtAuthenticationConfig();
	}
	
	@Autowired
	@Qualifier("authenticationManagerBean")
	private AuthenticationManager authenticationManager;

	@Bean
	public JwtAccessTokenConverter jwtAccessTokenConverter() {
		JwtAccessTokenConverter converter = new JwtAccessTokenConverter();
		//TODO move to spring config server
		converter.setSigningKey(config.getPrivateKey());
		converter.setVerifierKey(config.getPublicKey());
		
		
		//TODO do we have to move the keys into a keypair store?
//		KeyPair keyPair = new KeyStoreKeyFactory(
//				new ClassPathResource("keystore.jks"), "foobar".toCharArray())
//				.getKeyPair("test");
//		converter.setKeyPair(keyPair);
		return converter;
	}

	@Bean
	public JwtTokenStore tokenStore() {
		return new JwtTokenStore(jwtAccessTokenConverter());
	}

	@Override
	public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
		clients.inMemory().withClient(config.getHtml5ClientId()).secret(config.getHtml5ClientSecret()).scopes("read", "write", "openid")
				.authorizedGrantTypes("authorization_code", "password", "refresh_token").accessTokenValiditySeconds(config.getExpiration())
				.refreshTokenValiditySeconds(config.getRefreshExpiration());
				
	}

	@Override
	public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
		
		security.tokenKeyAccess("permitAll()").checkTokenAccess("isAuthenticated()");
	}

	@Override
	public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
		endpoints.authenticationManager(authenticationManager).tokenStore(tokenStore())
				.accessTokenConverter(jwtAccessTokenConverter());
	}
	
	

}
