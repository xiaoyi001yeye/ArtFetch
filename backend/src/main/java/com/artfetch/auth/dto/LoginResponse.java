package com.artfetch.auth.dto;

public record LoginResponse(
        String tokenName,
        String tokenValue,
        String tokenPrefix,
        long expiresIn,
        CurrentUserDto user
) {
}
