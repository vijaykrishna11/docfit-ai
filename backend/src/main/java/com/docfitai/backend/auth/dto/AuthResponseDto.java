package com.docfitai.backend.auth.dto;

public record AuthResponseDto(String accessToken, long expiresInSeconds, UserDto user) {
}
