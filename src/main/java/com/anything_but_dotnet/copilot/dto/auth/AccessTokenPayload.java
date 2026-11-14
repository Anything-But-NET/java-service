package com.anything_but_dotnet.copilot.dto.auth;

import com.anything_but_dotnet.copilot.model.DashboardOperatorAuthority;
import com.anything_but_dotnet.copilot.model.DashboardOperatorStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record AccessTokenPayload(
        String email,
        DashboardOperatorStatus status,
        List<DashboardOperatorAuthority> authorities
) implements UserDetails {

    public AccessTokenPayload {
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
    public @Nullable String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
