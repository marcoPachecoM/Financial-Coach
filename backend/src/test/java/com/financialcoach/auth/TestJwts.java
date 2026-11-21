package com.financialcoach.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Builds tokens with any claims, signed with the application's key (or another one), to test
 * each validation rule. The defaults produce a valid access token.
 */
record TestJwts(JwtEncoder encoder, JwtProperties properties) {

    String access(UUID userId, String role, Consumer<JwtClaimsSet.Builder> customize) {
        return sign(encoder, claims(userId, "access", customize.andThen(c -> c.claim("role", role))));
    }

    String refresh(UUID userId, Consumer<JwtClaimsSet.Builder> customize) {
        return sign(encoder, claims(userId, "refresh", customize));
    }

    /** A valid-looking access token signed with a different secret. */
    String signedWithAnotherKey(UUID userId) {
        byte[] otherKey = "another-secret-another-secret-0123456789".getBytes(StandardCharsets.UTF_8);
        JwtEncoder other = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(otherKey, "HmacSHA256")));
        return sign(other, claims(userId, "access", c -> c.claim("role", "CLIENT")));
    }

    /** Unsigned token ("alg": "none") with otherwise valid claims. */
    String unsigned(UUID userId) {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        long now = Instant.now().getEpochSecond();
        String header = base64.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = base64.encodeToString("""
                {"sub":"%s","iss":"%s","aud":["%s"],"iat":%d,"nbf":%d,"exp":%d,"jti":"%s","type":"access","role":"ADMIN"}
                """.formatted(userId, properties.issuer(), properties.audience(), now, now, now + 600, UUID.randomUUID())
                .strip().getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }

    private JwtClaimsSet claims(UUID userId, String type, Consumer<JwtClaimsSet.Builder> customize) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plusSeconds(600))
                .claim("type", type);
        customize.accept(claims);
        return claims.build();
    }

    private static String sign(JwtEncoder encoder, JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
