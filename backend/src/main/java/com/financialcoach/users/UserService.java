package com.financialcoach.users;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public entry point of the users module. */
@Service
public class UserService {

    private final UserRepository users;

    UserService(UserRepository users) {
        this.users = users;
    }

    /** Looks up a user by email, ignoring case. */
    @Transactional(readOnly = true)
    public Optional<UserCredentials> findCredentialsByEmail(String email) {
        return users.findByEmail(email)
                .map(user -> new UserCredentials(
                        user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole(), user.isActive()));
    }
}
