package com.anything_but_dotnet.copilot.service;

import com.anything_but_dotnet.copilot.dto.AclPayload;
import com.anything_but_dotnet.copilot.dto.UserAclUpsert;
import com.anything_but_dotnet.copilot.entity.User;
import com.anything_but_dotnet.copilot.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsersServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-10-26T18:00:00Z");

    @Mock
    private UsersRepository usersRepository;

    private UsersService usersService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        usersService = new UsersService(usersRepository, fixedClock);
    }

    @Test
    void shouldCreateNewUserOnAclUpsertEvent() {
        Instant eventUpdated = Instant.parse("2026-10-26T16:00:00Z");
        UserAclUpsert event = new UserAclUpsert(
                "oleh.pona@company.com",
                new AclPayload(List.of("devs", " all-employees ", "devs", "")),
                eventUpdated
        );

        when(usersRepository.findById("oleh.pona@company.com")).thenReturn(Optional.empty());

        usersService.processEvent(event);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(usersRepository).save(captor.capture());

        User saved = captor.getValue();
        assertEquals("oleh.pona@company.com", saved.getEmail());
        assertEquals(List.of("devs", "all-employees"), saved.getAllowedGroups());
        assertEquals(eventUpdated, saved.getLastUpdated());
        assertEquals(FIXED_NOW, saved.getLastSyncAt());
    }

    @Test
    void shouldUpdateExistingUserWhenEventIsNewer() {
        User existing = new User("oleh.pona@company.com");
        existing.applyUpsert(
                List.of("old-group"),
                Instant.parse("2026-10-26T15:00:00Z"),
                Instant.parse("2026-10-26T15:01:00Z")
        );

        when(usersRepository.findById("oleh.pona@company.com")).thenReturn(Optional.of(existing));

        Instant newerUpdated = Instant.parse("2026-10-26T17:00:00Z");
        UserAclUpsert event = new UserAclUpsert(
                "oleh.pona@company.com",
                new AclPayload(List.of("new-group")),
                newerUpdated
        );

        usersService.processEvent(event);

        verify(usersRepository).save(existing);
        assertEquals(List.of("new-group"), existing.getAllowedGroups());
        assertEquals(newerUpdated, existing.getLastUpdated());
        assertEquals(FIXED_NOW, existing.getLastSyncAt());
    }

    @Test
    void shouldSkipStaleUserAclUpsertEvent() {
        User existing = new User("oleh.pona@company.com");
        existing.applyUpsert(
                List.of("newer-group"),
                Instant.parse("2026-10-26T17:00:00Z"),
                Instant.parse("2026-10-26T17:01:00Z")
        );

        when(usersRepository.findById("oleh.pona@company.com")).thenReturn(Optional.of(existing));

        UserAclUpsert staleEvent = new UserAclUpsert(
                "oleh.pona@company.com",
                new AclPayload(List.of("older-group")),
                Instant.parse("2026-10-26T16:00:00Z")
        );

        usersService.processEvent(staleEvent);

        verify(usersRepository, never()).save(any());
        assertEquals(List.of("newer-group"), existing.getAllowedGroups());
    }

    @Test
    void shouldReturnUserGroupsIncludingEmailWhenUserExists() {
        User existing = new User("oleh.pona@company.com");
        existing.applyUpsert(List.of("devs", "all-employees"), FIXED_NOW, FIXED_NOW);
        when(usersRepository.findById("oleh.pona@company.com")).thenReturn(Optional.of(existing));

        List<String> groups = usersService.getUserGroups("oleh.pona@company.com");

        assertEquals(List.of("devs", "all-employees", "oleh.pona@company.com"), groups);
    }

    @Test
    void shouldReturnSingletonEmailListWhenUserNotFound() {
        when(usersRepository.findById("unknown@company.com")).thenReturn(Optional.empty());

        List<String> groups = usersService.getUserGroups("unknown@company.com");

        assertEquals(List.of("unknown@company.com"), groups);
    }
}
