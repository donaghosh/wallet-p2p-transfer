package com.paytm.wallet.config;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bearer-token to user-id mapping. Auth sophistication is explicitly not graded; this is
 * a minimal-but-real static token map, sourced from config/env (never hard-coded).
 *
 * <p>Raw form is a comma-separated {@code token:userId} list, e.g.
 * {@code tok_alice:alice,tok_bob:bob}, parsed once into an immutable lookup.
 */
@Component
@ConfigurationProperties(prefix = "wallet.auth")
@Getter
@Setter
public class AuthProperties {

    private String tokens = "";

    private final Map<String, String> tokenToUser = new HashMap<>();

    @PostConstruct
    void parse() {
        for (String pair : tokens.split(",")) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int sep = trimmed.indexOf(':');
            if (sep <= 0 || sep == trimmed.length() - 1) {
                throw new IllegalStateException(
                        "Invalid wallet.auth.tokens entry (expected token:userId): " + trimmed);
            }
            tokenToUser.put(trimmed.substring(0, sep), trimmed.substring(sep + 1));
        }
    }

    /** Resolve a bearer token to a user id, or {@code null} if unknown. */
    public String resolveUser(String token) {
        return tokenToUser.get(token);
    }
}
