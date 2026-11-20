package com.financialcoach.auth;

import com.financialcoach.users.UserRole;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The caller of a request. Controllers receive it with {@code @AuthenticationPrincipal}.
 *
 * <p>Today it is built from HTTP Basic credentials; when JWT login is added it will be built
 * from the token instead, and controllers will not change.
 */
public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

    private final UUID id;
    private final String email;
    private final UserRole role;
    private final boolean active;
    private String passwordHash;

    public AuthenticatedUser(UUID id, String email, UserRole role, boolean active, String passwordHash) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.active = active;
        this.passwordHash = passwordHash;
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public UserRole role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    /** Called by Spring Security after authentication, so the hash does not stay in memory. */
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public String toString() {
        return "AuthenticatedUser[id=%s, role=%s]".formatted(id, role);
    }
}
