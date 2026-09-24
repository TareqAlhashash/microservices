package com.investorbook.authservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * access_token, not accessToken, on the wire: api-gateway's LoginService deserializes this
 * response directly into its own AuthResponse and relays it to the browser unchanged, and the
 * frontend expects access_token there (see api-gateway's AuthResponse).
 */
public record TokenResponse(@JsonProperty("access_token") String accessToken) {
}
