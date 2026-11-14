package com.anything_but_dotnet.copilot.security;

import com.anything_but_dotnet.copilot.dto.auth.AccessTokenPayload;
import com.anything_but_dotnet.copilot.dto.auth.RefreshTokenPayload;
import com.anything_but_dotnet.copilot.exception.JwtException;
import com.anything_but_dotnet.copilot.model.DashboardOperatorAuthority;
import com.anything_but_dotnet.copilot.model.DashboardOperatorStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Component
public class JwtService {

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final Duration accessTokenExpiration;
    private final Duration refreshTokenExpiration;
    private final Clock clock;

    public JwtService(
            @Value("${app.security.access_key:fallback-security-key-development-only}") String accessKey,
            @Value("${app.security.refresh_key:fallback-security-key-development-only}") String refreshKey,
            @Value("${app.security.access_expiration:600}") Integer accessTokenExpiration,
            @Value("${app.security.refresh_expiration:5184000}") Integer refreshTokenExpiration,
            Clock clock
    ) {
        this.accessKey = Keys.hmacShaKeyFor(accessKey.getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(refreshKey.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = Duration.ofSeconds(accessTokenExpiration);
        this.refreshTokenExpiration = Duration.ofSeconds(refreshTokenExpiration);
        this.clock = clock;
    }

    public String generateRefreshToken (RefreshTokenPayload payload) {
        return Jwts.builder()
                .subject(payload.email())
                .issuedAt(Date.from(clock.instant()))
                .expiration(Date.from(clock.instant().plus(refreshTokenExpiration)))
                .signWith(refreshKey, Jwts.SIG.HS256).compact();
    }

    public String generateAccessToken (AccessTokenPayload payload) {
        return Jwts.builder()
                .claim("status", payload.status())
                .claim("authorities", payload.authorities())
                .subject(payload.email())
                .issuedAt(Date.from(clock.instant()))
                .expiration(Date.from(clock.instant().plus(accessTokenExpiration)))
                .signWith(accessKey, Jwts.SIG.HS256).compact();
    }

    public AccessTokenPayload parseAccessToken (String token) {
        Claims claims = getAndValidate(token, accessKey);

        var email = claims.getSubject();
        var status = claims.get("status", String.class);
        List<?> authorities = claims.get("authorities", List.class);

        if (email == null || status == null || authorities == null) {
            throw new JwtException(JwtException.JwtErrorKind.BAD_JWT, "Invalid token claims");
        }

        try {
            return new AccessTokenPayload(
                    claims.getSubject(),
                    DashboardOperatorStatus.valueOf(status),
                    authorities.stream()
                            .filter(Objects::nonNull)
                            .map(Object::toString)
                            .map(DashboardOperatorAuthority::valueOf)
                            .toList()
            );
        } catch (IllegalArgumentException e) {
            throw new JwtException(JwtException.JwtErrorKind.BAD_JWT, "Invalid token claims");
        }
    }

    public RefreshTokenPayload parseRefreshToken (String token) {
        Claims claims = getAndValidate(token, refreshKey);
        var email = claims.getSubject();

        if (email == null) {
            throw new JwtException(JwtException.JwtErrorKind.BAD_JWT, "Invalid token claims");
        }

        return new RefreshTokenPayload(email);
    }

    private static Claims getAndValidate(String token, SecretKey secretKey) {
        Jws<Claims> jwt;
        try {
            jwt = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
        } catch (ExpiredJwtException e) {
            throw new JwtException(JwtException.JwtErrorKind.EXPIRED, "Token has expired");
        }
        catch (io.jsonwebtoken.JwtException e) {
            throw new JwtException(JwtException.JwtErrorKind.VALIDATION_ERR, "Failed validating jwt", e);
        }
        catch (Exception e) {
            throw new JwtException(JwtException.JwtErrorKind.BAD_JWT, "Invalid JWT token", e);
        }

        return jwt.getPayload();
    }

    public Integer getExpirationOfAccessToken() {
        return Math.toIntExact(accessTokenExpiration.getSeconds());
    }

    public Integer getExpirationOfRefreshToken() {
        return Math.toIntExact(refreshTokenExpiration.getSeconds());
    }
}
