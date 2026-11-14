package com.anything_but_dotnet.copilot.exception;

import org.springframework.security.core.AuthenticationException;

public class JwtException extends AuthenticationException {
    public enum JwtErrorKind {
        VALIDATION_ERR,
        EXPIRED,
        BAD_JWT,
    }

    private final JwtErrorKind jwtErrorKind;
    public JwtException(JwtErrorKind jwtErrorKind, String message) {
        super(message);
        this.jwtErrorKind = jwtErrorKind;
    }
    public JwtException(JwtErrorKind jwtErrorKind, String message, Throwable cause) {
        super(message, cause);
        this.jwtErrorKind = jwtErrorKind;
    }

    public JwtErrorKind getJwtErrorKind() {
        return jwtErrorKind;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s",jwtErrorKind.toString(), super.toString());
    }
}

