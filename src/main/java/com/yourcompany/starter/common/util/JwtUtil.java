package com.yourcompany.starter.common.util;

import com.yourcompany.starter.config.JwtConfig;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtConfig jwtConfig;

    public String generateToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpirationMs()))
                .signWith(getSigningKey())
                .compact();
    }

    public Optional<String> extractUsername(String token) {
        return extractClaims(token).map(Claims::getSubject);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        return extractClaims(token)
                .map(claims -> claims.getSubject().equals(userDetails.getUsername())
                        && !claims.getExpiration().before(new Date()))
                .orElse(false);
    }

    private Optional<Claims> extractClaims(String token) {
        try {
            return Optional.of(
                    Jwts.parser()
                            .verifyWith(getSigningKey())
                            .build()
                            .parseSignedClaims(token)
                            .getPayload()
            );
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtConfig.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
