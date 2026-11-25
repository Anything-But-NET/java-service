package com.anything_but_dotnet.copilot.service.escalation;

public interface EscalationDestination {

    String destinationType();

    void deliver(EscalationRecordContext context);
}
