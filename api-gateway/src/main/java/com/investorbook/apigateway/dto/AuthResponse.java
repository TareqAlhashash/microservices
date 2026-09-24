package com.investorbook.apigateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The one field auth-service's /login (and this gateway's own /login, relaying it unchanged) and
 * the React frontend actually use. access_token, not accessToken, on the wire: the frontend
 * expects that exact key.
 */
public record AuthResponse(@JsonProperty("access_token") String accessToken) {
}
