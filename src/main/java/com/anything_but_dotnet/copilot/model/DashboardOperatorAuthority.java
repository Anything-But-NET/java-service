package com.anything_but_dotnet.copilot.model;

/**
 * Authorities granted to a web dashboard operator (matches {@code core.dashboard_operator_authority}).
 */
public enum DashboardOperatorAuthority {
    LOGS,
    METADATA,
    USERS,
    OPERATORS;

    public String getAuthority() {
        return switch (this){
            case LOGS -> "LOGS";
            case METADATA -> "METADATA";
            case USERS -> "USERS";
            case OPERATORS -> "OPERATORS";
        };
    }
}
