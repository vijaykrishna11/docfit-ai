package com.docfitai.backend.auth;

/** The authenticated principal derived from a validated access token -- never trust a client-supplied user id. */
public record AuthenticatedUser(Long userId, String email) {
}
