package com.yourcompany.starter.user.dto;

import com.yourcompany.starter.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class UserResponse {

    private final Long id;
    private final String username;
    private final String email;
    private final List<String> roles;
    private final Instant createdAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles().stream()
                        .map(Enum::name)
                        .collect(Collectors.toList()))
                .createdAt(user.getCreatedAt())
                .build();
    }
}
