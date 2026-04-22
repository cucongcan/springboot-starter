package com.yourcompany.starter.token.service;

import com.yourcompany.starter.common.exception.UnauthorizedException;
import com.yourcompany.starter.config.JwtConfig;
import com.yourcompany.starter.token.entity.RefreshToken;
import com.yourcompany.starter.token.repository.RefreshTokenRepository;
import com.yourcompany.starter.user.entity.User;
import com.yourcompany.starter.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtConfig jwtConfig;

    public RefreshToken createRefreshToken(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));

        refreshTokenRepository.findByUser(user)
                .ifPresent(refreshTokenRepository::delete);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusMillis(jwtConfig.getRefreshExpirationMs()))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional(readOnly = true)
    public RefreshToken findByToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new UnauthorizedException("Refresh token not found"));
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.isRevoked() || token.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new UnauthorizedException("Refresh token expired or revoked. Please login again.");
        }
        return token;
    }

    public void revokeByUser(User user) {
        refreshTokenRepository.revokeAllByUser(user);
    }

    public void deleteByUser(User user) {
        refreshTokenRepository.deleteByUser(user);
    }
}
