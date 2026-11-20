package com.financialcoach.users;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserRepository extends JpaRepository<User, UUID> {

    // Explicit lower() instead of a derived "IgnoreCase" query (which uses upper()),
    // so PostgreSQL can use the ux_users_email index on lower(email).
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmail(@Param("email") String email);
}
