package com.financialcoach.auth;

import com.financialcoach.users.UserRole;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT signing and validation (HS256 with a shared secret). The {@link JwtDecoder} bean validates
 * access tokens and is used by Spring Security on every request; refresh tokens are validated by
 * TokenService with {@link #decoder(JwtProperties, TokenType)}.
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, RateLimitProperties.class, CorsProperties.class})
class JwtConfig {

    static final String TYPE_CLAIM = "type";
    static final String ROLE_CLAIM = "role";

    private static final List<String> REQUIRED_CLAIMS = List.of(
            JwtClaimNames.SUB, JwtClaimNames.JTI, JwtClaimNames.ISS, JwtClaimNames.AUD,
            JwtClaimNames.IAT, JwtClaimNames.NBF, JwtClaimNames.EXP, TYPE_CLAIM);

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    /** Access tokens only: a refresh token sent to a normal endpoint is rejected. */
    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        return decoder(properties, TokenType.ACCESS);
    }

    /**
     * Accepts only HS256 tokens signed with our key (so "alg": "none" or another algorithm is
     * rejected), not expired, with our issuer and audience, all required claims and this type.
     */
    static JwtDecoder decoder(JwtProperties properties, TokenType type) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        validators.add(new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                audience -> audience != null && audience.contains(properties.audience())));
        validators.add(new JwtClaimValidator<String>(TYPE_CLAIM, type.claim()::equals));
        validators.add(new JwtClaimValidator<String>(JwtClaimNames.SUB, JwtConfig::isUuid));
        REQUIRED_CLAIMS.forEach(claim -> validators.add(requiredClaim(claim)));
        if (type == TokenType.ACCESS) {
            validators.add(new JwtClaimValidator<String>(ROLE_CLAIM, JwtConfig::isRole));
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    private static SecretKey secretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static OAuth2TokenValidator<Jwt> requiredClaim(String claim) {
        return jwt -> Objects.nonNull(jwt.getClaims().get(claim))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Missing claim: " + claim, null));
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    private static boolean isRole(String value) {
        return value != null && Arrays.stream(UserRole.values()).anyMatch(role -> role.name().equals(value));
    }
}
