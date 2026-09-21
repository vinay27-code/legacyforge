package com.legacyforge.security;

import com.legacyforge.auth.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Minimal UserDetails implementation. Wraps the id, email, and role that
 * we pull from the JWT — we do not hit the DB for every authenticated request.
 */
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final User.Role role;

    public UserPrincipal(UUID id, String email, User.Role role) {
        this.id = id;
        this.email = email;
        this.role = role;
    }

    public UUID getId() { return id; }
    public User.Role getRole() { return role; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String getPassword() { return ""; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
