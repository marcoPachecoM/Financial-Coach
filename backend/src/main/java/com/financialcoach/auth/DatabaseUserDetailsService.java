package com.financialcoach.auth;

import com.financialcoach.users.UserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads users from the database for HTTP Basic. The "username" is the email. */
@Service
class DatabaseUserDetailsService implements UserDetailsService {

    private final UserService userService;

    DatabaseUserDetailsService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return userService.findCredentialsByEmail(email)
                .map(user -> new AuthenticatedUser(
                        user.id(), user.email(), user.role(), user.active(), user.passwordHash()))
                // The message never reaches the client: every failure becomes the same 401.
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
