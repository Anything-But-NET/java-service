package com.anything_but_dotnet.copilot.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    @Test
    void shouldApplyUpsertAndDetectStaleEvents() {
        User user = new User("oleh.pona@company.com");
        Instant updatedTime = Instant.parse("2026-10-26T12:00:00Z");
        Instant syncTime = Instant.parse("2026-10-26T12:05:00Z");

        user.applyUpsert(List.of("devs", "all-employees"), updatedTime, syncTime);

        assertEquals("oleh.pona@company.com", user.getEmail());
        assertEquals(List.of("devs", "all-employees"), user.getAllowedGroups());
        assertEquals(updatedTime, user.getLastUpdated());
        assertEquals(syncTime, user.getLastSyncAt());

        assertTrue(user.isStale(Instant.parse("2026-10-26T11:59:59Z")));
        assertFalse(user.isStale(Instant.parse("2026-10-26T12:00:00Z")));
        assertFalse(user.isStale(Instant.parse("2026-10-26T12:00:01Z")));
    }

    @Test
    void shouldRejectNullEmailOrSyncTime() {
        assertThrows(NullPointerException.class, () -> new User(null));

        User user = new User("oleh.pona@company.com");
        assertThrows(
                NullPointerException.class,
                () -> user.applyUpsert(List.of("devs"), Instant.now(), null)
        );
    }
}
