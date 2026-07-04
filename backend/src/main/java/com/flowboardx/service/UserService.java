package com.flowboardx.service;

import com.flowboardx.domain.entity.User;
import com.flowboardx.domain.enums.UserRole;
import com.flowboardx.dto.AuthRequest;
import com.flowboardx.dto.AuthResponse;
import com.flowboardx.exception.DuplicateResourceException;
import com.flowboardx.exception.InvalidCredentialsException;
import com.flowboardx.repository.UserRepository;
import com.flowboardx.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthResponse register(AuthRequest request) {
        if (userRepo.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName() != null ? request.getDisplayName() : request.getEmail().split("@")[0])
                .role(UserRole.EDITOR)
                .build();
        user = userRepo.save(user);
        String token = jwtUtil.generateToken(user.getEmail());
        log.info("User registered userId={} email={}", user.getId(), user.getEmail());
        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole().name())
                .token(token)
                .build();
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepo.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        String token = jwtUtil.generateToken(user.getEmail());
        log.info("User logged in userId={} email={}", user.getId(), user.getEmail());
        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole().name())
                .token(token)
                .build();
    }

    public User loadByEmail(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("User not found: " + email));
    }
}