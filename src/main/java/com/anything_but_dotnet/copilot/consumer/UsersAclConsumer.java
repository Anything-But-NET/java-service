package com.anything_but_dotnet.copilot.consumer;

import com.anything_but_dotnet.copilot.dto.UserAclUpsert;
import com.anything_but_dotnet.copilot.service.UsersService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UsersAclConsumer {
    private static final Logger logger = LoggerFactory.getLogger(UsersAclConsumer.class);
    private final UsersService usersService;
    private final ObjectMapper objectMapper;

    public UsersAclConsumer(UsersService usersService, ObjectMapper objectMapper) {
        this.usersService = usersService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @KafkaListener(
            topics = "${app.kafka.topics.user_acl:rag.users.acl.v1}"
    )
    public void handleUserAcl(String payload) {
        try {
            UserAclUpsert event = objectMapper.readValue(payload, UserAclUpsert.class);

            if (event.email() == null || event.email().isBlank()) {
                throw new IllegalArgumentException("email cannot be null or empty");
            }

            usersService.processEvent(event);

            logger.info("User acl updated for user={}", event.email());
        } catch (Exception e) {
            logger.error("Failed to process user acl event. Payload: {}", payload, e);
            throw new RuntimeException("Failed to process Kafka event", e);
        }
    }
}
