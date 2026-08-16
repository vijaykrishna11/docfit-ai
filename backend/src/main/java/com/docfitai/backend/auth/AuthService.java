package com.docfitai.backend.auth;

import com.docfitai.backend.account.AppUser;
import com.docfitai.backend.account.AppUserRepository;
import com.docfitai.backend.account.RefreshToken;
import com.docfitai.backend.account.RefreshTokenRepository;
import com.docfitai.backend.account.SavedProviderRepository;
import com.docfitai.backend.account.SavedSearchRepository;
import com.docfitai.backend.account.ShortlistRepository;
import com.docfitai.backend.auth.dto.UserDto;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SavedProviderRepository savedProviderRepository;
    private final SavedSearchRepository savedSearchRepository;
    private final ShortlistRepository shortlistRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthRateLimiter rateLimiter;
    private final long refreshTokenTtlSeconds;

    public AuthService(
            AppUserRepository appUserRepository,
            RefreshTokenRepository refreshTokenRepository,
            SavedProviderRepository savedProviderRepository,
            SavedSearchRepository savedSearchRepository,
            ShortlistRepository shortlistRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthRateLimiter rateLimiter,
            @Value("${docfitai.auth.refresh-token-ttl-days:30}") long refreshTokenTtlDays) {
        this.appUserRepository = appUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.savedProviderRepository = savedProviderRepository;
        this.savedSearchRepository = savedSearchRepository;
        this.shortlistRepository = shortlistRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.refreshTokenTtlSeconds = refreshTokenTtlDays * 24 * 60 * 60;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    @Transactional
    public AuthResult register(String rawEmail, String password, String displayName, String clientIp) {
        String email = normalizeEmail(rawEmail);
        // Keyed by IP+email (not IP alone): stops bulk signups/brute-force against one address
        // without penalizing unrelated legitimate signups sharing a NAT'd IP.
        rateLimiter.checkAllowed("register:" + clientIp + ":" + email);
        if (appUserRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        Instant now = Instant.now();
        AppUser user = appUserRepository.save(
                new AppUser(email, passwordEncoder.encode(password), blankToNull(displayName), now, now));
        return issueTokens(user);
    }

    @Transactional
    public AuthResult login(String rawEmail, String password, String clientIp) {
        String email = normalizeEmail(rawEmail);
        rateLimiter.checkAllowed("login:" + clientIp + ":" + email);
        AppUser user = appUserRepository
                .findByEmail(email)
                .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));
        return issueTokens(user);
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        String hash = TokenHasher.hash(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository
                .findByTokenHash(hash)
                .filter(token -> token.isActive(Instant.now()))
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired. Please sign in again."));

        // Rotate: the presented token is single-use.
        existing.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existing);

        AppUser user = appUserRepository
                .findById(existing.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists."));
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = TokenHasher.hash(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    public UserDto getUser(Long userId) {
        return toDto(requireUser(userId));
    }

    @Transactional
    public UserDto updateDisplayName(Long userId, String displayName) {
        AppUser user = requireUser(userId);
        user.setDisplayName(blankToNull(displayName));
        user.setUpdatedAt(Instant.now());
        return toDto(user);
    }

    @Transactional
    public void deleteAccount(Long userId) {
        requireUser(userId);
        // shortlist_provider rows cascade at the DB level once their parent provider_shortlist
        // row is deleted (V11, ON DELETE CASCADE) -- deleting the shortlists themselves is enough.
        shortlistRepository.deleteByUserId(userId);
        savedProviderRepository.deleteByUserId(userId);
        savedSearchRepository.deleteByUserId(userId);
        refreshTokenRepository.deleteByUserId(userId);
        appUserRepository.deleteById(userId);
    }

    private AppUser requireUser(Long userId) {
        return appUserRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private AuthResult issueTokens(AppUser user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String rawRefreshToken = TokenHasher.generateRawToken();
        refreshTokenRepository.save(new RefreshToken(
                user.getId(),
                TokenHasher.hash(rawRefreshToken),
                Instant.now().plusSeconds(refreshTokenTtlSeconds),
                Instant.now()));
        return new AuthResult(accessToken, rawRefreshToken, toDto(user));
    }

    private static UserDto toDto(AppUser user) {
        return new UserDto(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
