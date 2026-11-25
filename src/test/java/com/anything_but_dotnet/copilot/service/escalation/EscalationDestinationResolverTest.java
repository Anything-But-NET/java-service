package com.anything_but_dotnet.copilot.service.escalation;

import com.anything_but_dotnet.copilot.config.EscalationProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EscalationDestinationResolverTest {

    @Test
    void resolvesDefaultDatabaseDestination() {
        EscalationProperties properties = new EscalationProperties();
        DatabaseQueueEscalationDestination databaseDestination = new DatabaseQueueEscalationDestination();

        EscalationDestinationResolver resolver = new EscalationDestinationResolver(
                properties,
                List.of(databaseDestination)
        );

        assertSame(databaseDestination, resolver.resolve());
        assertEquals("database", resolver.resolve().destinationType());
    }

    @Test
    void resolvesConfiguredCustomDestinationCaseInsensitively() {
        EscalationProperties properties = new EscalationProperties();
        properties.setDestination("  External-Desk ");

        DatabaseQueueEscalationDestination databaseDestination = new DatabaseQueueEscalationDestination();
        EscalationDestination customDestination = mock(EscalationDestination.class);
        when(customDestination.destinationType()).thenReturn("external-desk");

        EscalationDestinationResolver resolver = new EscalationDestinationResolver(
                properties,
                List.of(databaseDestination, customDestination)
        );

        assertSame(customDestination, resolver.resolve());
    }

    @Test
    void failsFastOnUnsupportedDestination() {
        EscalationProperties properties = new EscalationProperties();
        properties.setDestination("jira");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new EscalationDestinationResolver(properties, List.of(new DatabaseQueueEscalationDestination()))
        );

        assertTrue(ex.getMessage().contains("jira"));
        assertTrue(ex.getMessage().contains("database"));
    }

    @Test
    void failsFastOnDuplicateDestinationType() {
        EscalationProperties properties = new EscalationProperties();
        EscalationDestination first = mock(EscalationDestination.class);
        EscalationDestination second = mock(EscalationDestination.class);
        when(first.destinationType()).thenReturn("database");
        when(second.destinationType()).thenReturn("DATABASE");

        assertThrows(
                IllegalStateException.class,
                () -> new EscalationDestinationResolver(properties, List.of(first, second))
        );
    }

    @Test
    void rejectsNonPositiveDraftTtlAndFallsBackToDefaultOnBlankDestination() {
        EscalationProperties properties = new EscalationProperties();
        properties.setDestination("   ");
        assertEquals(EscalationProperties.DEFAULT_DESTINATION, properties.getDestination());

        assertThrows(IllegalArgumentException.class, () -> properties.setDraftTtl(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> properties.setDraftTtl(Duration.ofMinutes(-5)));
        assertThrows(IllegalArgumentException.class, () -> properties.setDraftTtl(null));
    }
}
