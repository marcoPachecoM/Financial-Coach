package com.financialcoach.auth;

import com.financialcoach.users.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authentication of a request with a valid access token. Its principal is an
 * {@link AuthenticatedUser}, so controllers do not depend on JWT classes.
 */
final class AccessTokenAuthentication extends AbstractAuthenticationToken {

    private final AuthenticatedUser user;

    private AccessTokenAuthentication(AuthenticatedUser user) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
        this.user = user;
        setAuthenticated(true);
    }

    /** The token is already validated by JwtConfig (sub is a UUID, role is a known role). */
    static AccessTokenAuthentication from(Jwt jwt) {
        UserRole role = UserRole.valueOf(jwt.getClaimAsString(JwtConfig.ROLE_CLAIM));
        return new AccessTokenAuthentication(new AuthenticatedUser(UUID.fromString(jwt.getSubject()), role));
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return user;
    }

    /** The token itself is not kept after validation. */
    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public String getName() {
        return user.id().toString();
    }
}
