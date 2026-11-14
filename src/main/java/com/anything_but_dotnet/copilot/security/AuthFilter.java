package com.anything_but_dotnet.copilot.security;

import com.anything_but_dotnet.copilot.model.DashboardOperatorStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AuthFilter extends OncePerRequestFilter {
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String HEADER = "Authorization";

    private final JwtService jwtService;
    private final DashboardSecurityHandlers securityHandlers;

    public AuthFilter(JwtService jwtService, DashboardSecurityHandlers securityHandlers) {
        this.jwtService = jwtService;
        this.securityHandlers = securityHandlers;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader(HEADER);
        if (authHeader == null || authHeader.isBlank() || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            SecurityContextHolder.clearContext();
            securityHandlers.commence(request, response, null);
            return;
        }

        try {
            var payload = jwtService.parseAccessToken(token);
            if (payload.status() == DashboardOperatorStatus.BLOCKED) {
                SecurityContextHolder.clearContext();
                securityHandlers.handle(request, response, new AccessDeniedException("Operator account is BLOCKED."));
                return;
            }

            var authentication = new UsernamePasswordAuthenticationToken(
                    payload, null, payload.getAuthorities()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            securityHandlers.commence(request, response, ex);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
