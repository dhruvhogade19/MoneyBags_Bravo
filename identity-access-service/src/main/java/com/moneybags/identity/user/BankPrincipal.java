package com.moneybags.identity.user;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record BankPrincipal(
        String userId,
        String username,
        String password,
        String customerId,
        String tenantId,
        boolean enabled,
        boolean accountNonLocked,
        Collection<? extends GrantedAuthority> authorities) implements UserDetails {

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public boolean isAccountNonLocked() { return accountNonLocked; }
}
