package com.mynotionai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class GoogleTokenVerifierService {

    @Value("${google.oauth.client-id:}")
    private String googleClientId;

    private final RestTemplate restTemplate = new RestTemplate();

    public GoogleUserInfo verifyIdToken(String idToken) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "GOOGLE_OAUTH_CLIENT_ID is not configured"
            );
        }

        Map<String, Object> tokenInfo;
        try {
            tokenInfo = restTemplate.getForObject(
                "https://oauth2.googleapis.com/tokeninfo?id_token={idToken}",
                Map.class,
                idToken
            );
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google id token");
        }

        if (tokenInfo == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google id token");
        }

        String audience = String.valueOf(tokenInfo.get("aud"));
        if (!googleClientId.equals(audience)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google audience mismatch");
        }

        String emailVerified = String.valueOf(tokenInfo.get("email_verified"));
        if (!"true".equalsIgnoreCase(emailVerified)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email is not verified");
        }

        String providerUserId = stringValue(tokenInfo.get("sub"));
        String email = stringValue(tokenInfo.get("email"));
        String name = stringValue(tokenInfo.get("name"));

        if (providerUserId.isBlank() || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token payload");
        }

        return new GoogleUserInfo(providerUserId, email, name);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record GoogleUserInfo(String providerUserId, String email, String name) {
    }
}
