package com.anything_but_dotnet.copilot.consumer;

import com.anything_but_dotnet.copilot.dto.UserAclUpsert;
import com.anything_but_dotnet.copilot.service.UsersService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UsersAclConsumerTest {

    @Mock
    private UsersService usersService;

    private UsersAclConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new UsersAclConsumer(usersService, objectMapper);
    }

    @Test
    void shouldParseAndForwardValidUserAclEvent() {
        String payload = """
                {
                  "email": "oleh.pona@company.com",
                  "acl": {
                    "allowed_groups": ["devs", "all-employees"]
                  },
                  "last_updated": "2026-10-26T16:00:00Z"
                }
                """;

        consumer.handleUserAcl(payload);

        ArgumentCaptor<UserAclUpsert> captor = ArgumentCaptor.forClass(UserAclUpsert.class);
        verify(usersService).processEvent(captor.capture());

        UserAclUpsert event = captor.getValue();
        assertEquals("oleh.pona@company.com", event.email());
        assertEquals(List.of("devs", "all-employees"), event.acl().normalizedGroups());
        assertEquals(Instant.parse("2026-10-26T16:00:00Z"), event.lastUpdated());
    }

    @Test
    void shouldThrowExceptionWhenEmailIsBlank() {
        String payload = """
                {
                  "email": "   ",
                  "acl": {
                    "allowed_groups": ["devs"]
                  },
                  "last_updated": "2026-10-26T16:00:00Z"
                }
                """;

        assertThrows(RuntimeException.class, () -> consumer.handleUserAcl(payload));
        verify(usersService, never()).processEvent(any());
    }

    @Test
    void shouldThrowExceptionWhenPayloadIsMalformed() {
        assertThrows(RuntimeException.class, () -> consumer.handleUserAcl("{broken-json"));
        verify(usersService, never()).processEvent(any());
    }
}
