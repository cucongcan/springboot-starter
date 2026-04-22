package com.yourcompany.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.jwt")
public class JwtConfig {

    private String secret;
    private long expirationMs;
    private long refreshExpirationMs;
}
