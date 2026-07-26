package com.biashara.iam.security;

import com.biashara.iam.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Issues and verifies access tokens.
 *
 * The token carries the caller's tenant, permission codes and role codes, so an
 * authenticated request is authorised without touching the database. The trade-off
 * is that a permission change only takes effect on the next token — which is why
 * access tokens are short-lived and refresh tokens are stored server-side and
 * revocable.
 */
@Service
public class JwtService {

    private static final String CLAIM_TENANT_ID = "tid";
    private static final String CLAIM_TENANT_NAME = "tname";
    private static final String CLAIM_FULL_NAME = "name";
    private static final String CLAIM_PERMISSIONS = "perms";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PLATFORM_ADMIN = "padmin";
    private static final String CLAIM_HIERARCHY = "hlvl";
    private static final String CLAIM_DEPARTMENT = "dept";
    private static final String CLAIM_BRANCH = "branch";

    private final SecretKey signingKey;
    private final long accessTokenMinutes;
    private final int refreshTokenDays;

    public JwtService(@Value("${biashara.jwt.secret}") String secret,
                      @Value("${biashara.jwt.access-token-minutes}") long accessTokenMinutes,
                      @Value("${biashara.jwt.refresh-token-days}") int refreshTokenDays) {
        this.signingKey = buildKey(secret);
        this.accessTokenMinutes = accessTokenMinutes;
        this.refreshTokenDays = refreshTokenDays;
    }

    /**
     * Accepts either a base64 secret or raw text, so the demo default stays
     * readable while a deployment can supply proper random bytes.
     */
    private static SecretKey buildKey(String secret) {
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(secret);
        } catch (RuntimeException notBase64) {
            // jjwt raises DecodingException, which is not an IllegalArgumentException,
            // so this has to catch broadly to fall back to raw bytes.
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "biashara.jwt.secret must be at least 32 bytes for HMAC-SHA signing");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_TENANT_ID, user.getTenant() == null ? null : user.getTenant().getId())
                .claim(CLAIM_TENANT_NAME, user.getTenant() == null ? null : user.getTenant().getName())
                .claim(CLAIM_FULL_NAME, user.getFullName())
                .claim(CLAIM_PERMISSIONS, List.copyOf(user.collectPermissionCodes()))
                .claim(CLAIM_ROLES, List.copyOf(user.collectRoleCodes()))
                .claim(CLAIM_PLATFORM_ADMIN, user.isPlatformAdmin())
                .claim(CLAIM_HIERARCHY, user.highestHierarchyLevel())
                .claim(CLAIM_DEPARTMENT, user.getDepartment() == null ? null : user.getDepartment().getId())
                .claim(CLAIM_BRANCH, user.getBranch() == null ? null : user.getBranch().getId())
                .issuer("biashara")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenMinutes, ChronoUnit.MINUTES)))
                .signWith(signingKey)
                .compact();
    }

    /** Opaque, high-entropy refresh token. Its authority comes from the database row. */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(refreshTokenDays, ChronoUnit.DAYS);
    }

    public long accessTokenSeconds() {
        return accessTokenMinutes * 60;
    }

    /** Empty when the token is malformed, expired, or not signed by this service. */
    public Optional<UserPrincipal> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer("biashara")
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(new UserPrincipal(
                    Long.valueOf(claims.getSubject()),
                    numeric(claims, CLAIM_TENANT_ID),
                    claims.get(CLAIM_TENANT_NAME, String.class),
                    claims.get("email", String.class),
                    claims.get(CLAIM_FULL_NAME, String.class),
                    Boolean.TRUE.equals(claims.get(CLAIM_PLATFORM_ADMIN, Boolean.class)),
                    claims.get(CLAIM_HIERARCHY, Integer.class) == null
                            ? Integer.MAX_VALUE
                            : claims.get(CLAIM_HIERARCHY, Integer.class),
                    numeric(claims, CLAIM_DEPARTMENT),
                    numeric(claims, CLAIM_BRANCH),
                    stringSet(claims, CLAIM_PERMISSIONS),
                    stringSet(claims, CLAIM_ROLES)));
        } catch (JwtException | IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private static Long numeric(Claims claims, String name) {
        Object raw = claims.get(name);
        return raw instanceof Number number ? number.longValue() : null;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> stringSet(Claims claims, String name) {
        Object raw = claims.get(name);
        if (raw instanceof List<?> list) {
            Set<String> values = new HashSet<>();
            list.forEach(item -> values.add(String.valueOf(item)));
            return values;
        }
        if (raw instanceof Map<?, ?> ignored) {
            return Set.of();
        }
        return Set.of();
    }
}
