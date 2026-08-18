package com.pharmacy.security;

import com.pharmacy.model.UserAccount;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Component
public class JwtService {
    private final SecretKey key;
    private final Duration ttl;

    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.ttl-minutes:480}") long ttlMinutes) {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalArgumentException("JWT Secret must be at least 32 characters (256-bit) long for HMAC-SHA security.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public String create(UserAccount user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.username)
                .claim("role", user.role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public UserDetails parse(String token) {
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new org.springframework.security.core.userdetails.User(
                claims.getSubject(),
                "",
                List.of(new SimpleGrantedAuthority("ROLE_" + claims.get("role", String.class))));
    }
}
