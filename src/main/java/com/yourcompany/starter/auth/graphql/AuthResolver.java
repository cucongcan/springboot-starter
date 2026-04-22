package com.yourcompany.starter.auth.graphql;

import com.yourcompany.starter.auth.dto.AuthResponse;
import com.yourcompany.starter.auth.dto.LoginRequest;
import com.yourcompany.starter.auth.dto.RegisterRequest;
import com.yourcompany.starter.auth.service.AuthService;
import com.yourcompany.starter.user.dto.UserResponse;
import com.yourcompany.starter.user.entity.User;
import com.yourcompany.starter.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class AuthResolver {

    private final AuthService authService;
    private final UserRepository userRepository;

    @MutationMapping
    public AuthResponse register(
            @Argument String username,
            @Argument String email,
            @Argument String password) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);
        return authService.register(request);
    }

    @MutationMapping
    public AuthResponse login(
            @Argument String username,
            @Argument String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return authService.login(request);
    }

    @MutationMapping
    public AuthResponse refreshToken(@Argument String refreshToken) {
        return authService.refreshToken(refreshToken);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public boolean logout(
            @Argument String refreshToken,
            @AuthenticationPrincipal UserDetails userDetails) {
        authService.logout(refreshToken);
        return true;
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public UserResponse me(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException(userDetails.getUsername()));
        return UserResponse.from(user);
    }
}
