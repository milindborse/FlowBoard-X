package com.flowboardx.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter @Builder
public class AuthResponse {
    private UUID userId;
    private String email;
    private String displayName;
    private String role;
    private String token;
}
