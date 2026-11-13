package com.anything_but_dotnet.copilot.security;

import com.anything_but_dotnet.copilot.model.DashboardOperatorAuthority;
import com.anything_but_dotnet.copilot.model.DashboardOperatorStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record OperatorDetailsImpl(
        String email,
        DashboardOperatorStatus status,
        String password,
        List<DashboardOperatorAuthority> authorities
) implements UserDetails {

    public OperatorDetailsImpl {
        authorities = authorities != null ? List.copyOf(authorities) : List.of();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities.stream()
                .map(DashboardOperatorAuthority::getAuthority)
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != DashboardOperatorStatus.BLOCKED;
    }
}
