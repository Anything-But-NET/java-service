package com.anything_but_dotnet.copilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.escalation")
public class EscalationProperties {

    public static final String DEFAULT_DESTINATION = "database";
    public static final Duration DEFAULT_DRAFT_TTL = Duration.ofMinutes(15);

    private String destination = DEFAULT_DESTINATION;
    private Duration draftTtl = DEFAULT_DRAFT_TTL;

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = (destination == null || destination.isBlank())
                ? DEFAULT_DESTINATION
                : destination.trim();
    }

    public Duration getDraftTtl() {
        return draftTtl;
    }

    public void setDraftTtl(Duration draftTtl) {
        if (draftTtl == null || draftTtl.isNegative() || draftTtl.isZero()) {
            throw new IllegalArgumentException("app.escalation.draft-ttl must be positive.");
        }
        this.draftTtl = draftTtl;
    }
}
