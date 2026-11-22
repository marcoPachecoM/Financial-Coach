package com.financialcoach.auth;

import com.financialcoach.shared.error.UnauthorizedException;
import com.financialcoach.users.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/**
 * Creates and reads JWTs. Claims (see securitySchemes in openapi.yaml): sub (user id), type,
 * iss, aud, jti, iat, nbf, exp, and role in access tokens. Does not touch the database.
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final JwtDecoder refreshTokenDecoder;
    private final JwtProperties properties;
    private final Clock clock;

    TokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.refreshTokenDecoder = JwtConfig.decoder(properties, TokenType.REFRESH, clock);
        this.properties = properties;
        this.clock = clock;
    }

    /** Public so integration tests can authenticate requests without going through login. */
    public String createAccessToken(UUID userId, UserRole role) {
        Instant now = clock.instant();
        JwtClaimsSet claims = baseClaims(userId, TokenType.ACCESS, now, now.plus(properties.accessTokenTtl()))
                .claim(JwtConfig.ROLE_CLAIM, role.name())
                .build();
        return encode(claims);
    }

    IssuedRefreshToken createRefreshToken(UUID userId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.refreshTokenTtl());
        String jti = UUID.randomUUID().toString();
        JwtClaimsSet claims = baseClaims(userId, TokenType.REFRESH, now, expiresAt).id(jti).build();
        return new IssuedRefreshToken(encode(claims), jti, expiresAt);
    }

    /** Signature, expiry, issuer, audience and type=refresh; any failure is INVALID_TOKEN. */
    Jwt decodeRefreshToken(String token) {
        try {
            return refreshTokenDecoder.decode(token);
        } catch (JwtException e) {
            throw new UnauthorizedException("INVALID_TOKEN", "The refresh token is invalid or expired.");
        }
    }

    private JwtClaimsSet.Builder baseClaims(UUID userId, TokenType type, Instant now, Instant expiresAt) {
        return JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(expiresAt)
                .claim(JwtConfig.TYPE_CLAIM, type.claim());
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    record IssuedRefreshToken(String token, String jti, Instant expiresAt) {
    }
}
