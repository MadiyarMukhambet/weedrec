package kz.weedrec.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** The current user's id from the JWT. */
public final class CurrentUser {
    private CurrentUser() {}

    public static Optional<String> idIfPresent() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a instanceof AnonymousAuthenticationToken || !a.isAuthenticated()) return Optional.empty();
        return Optional.of((String) a.getPrincipal());
    }

    public static String id() {
        return idIfPresent().orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }
}
