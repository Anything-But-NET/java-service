package com.anything_but_dotnet.copilot.service.escalation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DatabaseQueueEscalationDestination implements EscalationDestination {

    public static final String TYPE = "database";
    private static final Logger logger = LoggerFactory.getLogger(DatabaseQueueEscalationDestination.class);

    @Override
    public String destinationType() {
        return TYPE;
    }

    @Override
    public void deliver(EscalationRecordContext context) {
        logger.info(
                "Recorded escalation {} for query_id={} in destination '{}' (citations={})",
                context.escalationId(),
                context.queryId(),
                TYPE,
                context.sourceReferences().size()
        );
    }
}
