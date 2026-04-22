package com.yourcompany.starter.auth.service;

import com.yourcompany.starter.auth.dto.AuthResponse;
import com.yourcompany.starter.auth.dto.LoginRequest;
import com.yourcompany.starter.auth.dto.RegisterRequest;
import com.yourcompany.starter.common.util.JwtUtil;
import com.yourcompany.starter.token.entity.RefreshToken;
import com.yourcompany.starter.token.service.RefreshTokenService;
import com.yourcompany.starter.user.entity.Role;
import com.yourcompany.starter.user.entity.User;
import com.yourcompany.starter.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(Set.of(Role.ROLE_USER))
                .build();

        userRepository.save(user);

        return AuthResponse.builder()
                .message("Registration successful. Please login.")
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        String accessToken = jwtUtil.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getUsername());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .build();
    }

    public AuthResponse refreshToken(String tokenValue) {
        RefreshToken refreshToken = refreshTokenService.findByToken(tokenValue);
        refreshTokenService.verifyExpiration(refreshToken);

        UserDetails userDetails = refreshToken.getUser();
        String newAccessToken = jwtUtil.generateToken(userDetails);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .build();
    }

    public void logout(String tokenValue) {
        RefreshToken refreshToken = refreshTokenService.findByToken(tokenValue);
        refreshTokenService.deleteByUser(refreshToken.getUser());
    }
}
