package com.docfitai.backend.auth;

import com.docfitai.backend.auth.dto.AuthResponseDto;
import com.docfitai.backend.auth.dto.LoginRequest;
import com.docfitai.backend.auth.dto.RegisterRequest;
import com.docfitai.backend.auth.dto.UpdateDisplayNameRequest;
import com.docfitai.backend.auth.dto.UserDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "docfit_refresh";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final JwtService jwtService;
    private final boolean cookieSecure;

    public AuthController(
            AuthService authService, JwtService jwtService, @Value("${docfitai.auth.cookie-secure:false}") boolean cookieSecure) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        AuthResult result = authService.register(
                request.email(), request.password(), request.displayName(), clientIp(httpRequest));
        setRefreshCookie(response, result.rawRefreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
    }

    @PostMapping("/login")
    public AuthResponseDto login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        AuthResult result = authService.login(request.email(), request.password(), clientIp(httpRequest));
        setRefreshCookie(response, result.rawRefreshToken());
        return toResponse(result);
    }

    @PostMapping("/refresh")
    public AuthResponseDto refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken, HttpServletResponse response) {
        if (refreshToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing refresh token.");
        }
        AuthResult result = authService.refresh(refreshToken);
        setRefreshCookie(response, result.rawRefreshToken());
        return toResponse(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken, HttpServletResponse response) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserDto me(Authentication authentication) {
        return authService.getUser(requireUserId(authentication));
    }

    @PatchMapping("/me")
    public UserDto updateMe(Authentication authentication, @Valid @RequestBody UpdateDisplayNameRequest request) {
        return authService.updateDisplayName(requireUserId(authentication), request.displayName());
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(Authentication authentication, HttpServletResponse response) {
        authService.deleteAccount(requireUserId(authentication));
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal.userId();
    }

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private AuthResponseDto toResponse(AuthResult result) {
        return new AuthResponseDto(result.accessToken(), jwtService.getAccessTokenTtlSeconds(), result.user());
    }

    private void setRefreshCookie(HttpServletResponse response, String rawToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(authService.getRefreshTokenTtlSeconds())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
