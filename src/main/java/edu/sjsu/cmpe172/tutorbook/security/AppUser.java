package edu.sjsu.cmpe172.tutorbook.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The logged-in user, built from the users row (and providers row for tutors).
 * Spring Security stores it in the HTTP session after a successful login, and
 * controllers receive it with @AuthenticationPrincipal.
 */
public record AppUser(
        long userId,
        String email,
        String passwordHash,
        String fullName,
        String role,
        Long providerId) implements UserDetails {

    public boolean isCustomer() {
        return "CUSTOMER".equals(role);
    }

    public boolean isProvider() {
        return "PROVIDER".equals(role);
    }

    /** role CUSTOMER becomes authority ROLE_CUSTOMER, which hasRole("CUSTOMER") checks. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
