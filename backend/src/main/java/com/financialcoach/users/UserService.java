package com.financialcoach.users;

import com.financialcoach.shared.error.ConflictException;
import com.financialcoach.shared.error.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public entry point of the users module. */
@Service
public class UserService {

    private static final String EMAIL_TAKEN = "An account with this email already exists.";
    private static final String USERNAME_TAKEN = "This username is already taken.";

    private final UserRepository users;

    UserService(UserRepository users) {
        this.users = users;
    }

    /** Looks up a user by email, ignoring case. */
    @Transactional(readOnly = true)
    public Optional<UserCredentials> findCredentialsByEmail(String email) {
        return users.findByEmail(email).map(UserService::credentials);
    }

    @Transactional(readOnly = true)
    public Optional<UserCredentials> findCredentials(UUID id) {
        return users.findById(id).map(UserService::credentials);
    }

    @Transactional(readOnly = true)
    public Optional<UserProfile> findProfile(UUID id) {
        return users.findById(id)
                .map(user -> new UserProfile(
                        user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.isActive()));
    }

    /**
     * Sign-up: a new active CLIENT. Email and username are unique ignoring case (409). The checks
     * give a clean answer; the unique indexes are the real guarantee when two requests race.
     * {@code passwordHash} must already be hashed.
     */
    @Transactional
    public UserProfile register(String username, String email, String passwordHash) {
        if (users.existsByEmail(email)) {
            throw new ConflictException(EMAIL_TAKEN);
        }
        if (users.existsByUsername(username)) {
            throw new ConflictException(USERNAME_TAKEN);
        }
        User user;
        try {
            user = users.saveAndFlush(new User(username, email, passwordHash, UserRole.CLIENT));
        } catch (DataIntegrityViolationException e) {
            String message = String.valueOf(e.getMessage());
            if (message.contains("ux_users_email")) {
                throw new ConflictException(EMAIL_TAKEN);
            }
            if (message.contains("ux_users_username")) {
                throw new ConflictException(USERNAME_TAKEN);
            }
            throw e;
        }
        return new UserProfile(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.isActive());
    }

    /** {@code passwordHash} must already be hashed. 404 when the user does not exist. */
    @Transactional
    public void changePasswordHash(UUID id, String passwordHash) {
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("User not found."));
        user.changePasswordHash(passwordHash);
        users.saveAndFlush(user);
    }

    private static UserCredentials credentials(User user) {
        return new UserCredentials(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole(), user.isActive());
    }
}
