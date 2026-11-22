package com.financialcoach.health;

import com.financialcoach.auth.AuthenticatedUser;
import com.financialcoach.users.UserRole;
import org.springframework.core.env.Environment;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health endpoints of the API contract. They also exercise each security level:
 * public, authenticated and admin-only (rules in SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/health")
class HealthController {

    private final Environment environment;

    HealthController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping
    AppHealth health() {
        String[] profiles = environment.getActiveProfiles();
        return new AppHealth("ok", profiles.length > 0 ? String.join(",", profiles) : "default");
    }

    @GetMapping("/public")
    Status publicHealth() {
        return new Status("ok");
    }

    @GetMapping("/protected")
    RoleStatus protectedHealth(@AuthenticationPrincipal AuthenticatedUser user) {
        return new RoleStatus("ok", user.role());
    }

    @GetMapping("/admin")
    ScopeStatus adminHealth() {
        return new ScopeStatus("ok", "admin");
    }

    record AppHealth(String status, String environment) {
    }

    record Status(String status) {
    }

    record RoleStatus(String status, UserRole role) {
    }

    record ScopeStatus(String status, String scope) {
    }
}
