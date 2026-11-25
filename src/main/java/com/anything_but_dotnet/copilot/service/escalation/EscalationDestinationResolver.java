package com.anything_but_dotnet.copilot.service.escalation;

import com.anything_but_dotnet.copilot.config.EscalationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class EscalationDestinationResolver {

    private final EscalationDestination activeDestination;

    public EscalationDestinationResolver(
            EscalationProperties properties,
            List<EscalationDestination> destinations
    ) {
        Map<String, EscalationDestination> byType = destinations.stream()
                .collect(Collectors.toMap(
                        destination -> destination.destinationType().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (first, second) -> {
                            throw new IllegalStateException(
                                    "Duplicate EscalationDestination registered for type: " + first.destinationType()
                            );
                        }
                ));

        String configured = properties.getDestination().toLowerCase(Locale.ROOT);
        EscalationDestination resolved = byType.get(configured);
        if (resolved == null) {
            throw new IllegalStateException(
                    "Unsupported escalation destination '" + properties.getDestination()
                            + "'. Available destinations: " + byType.keySet()
            );
        }
        this.activeDestination = resolved;
    }

    public EscalationDestination resolve() {
        return activeDestination;
    }
}
