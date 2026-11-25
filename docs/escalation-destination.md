# Escalation Record & Ticket Destination (SCRUM-37)

## Agreed MVP Ticket Destination

The team's agreed MVP destination for confirmed escalations is Core's PostgreSQL expert queue (`core.escalations` with audit history in `core.escalation_status_history`), exposed to experts in the Web Dashboard via:

- `GET /api/v1/dash/escalations` — paginated expert queue
- `GET /api/v1/dash/escalations/{id}` — full escalation record context and lifecycle history
- `PATCH /api/v1/dash/escalations/{id}/status` — status lifecycle transitions (`PENDING` -> `IN_PROGRESS` -> `RESOLVED` -> `CLOSED`, or `CANCELLED`)

## Confirmation Flow & Context Captured

1. **Question processing (`POST /api/v1/chat/question`):**
   - Core stores a temporary escalation draft in `core.escalation_drafts` with a configurable TTL (`app.escalation.draft-ttl`, default `15m`).
   - No escalation ticket is created until the employee explicitly confirms.
   - Expired drafts are automatically purged on a schedule.

2. **User confirmation (`POST /api/v1/chat/escalations`):**
   - Idempotently promotes the active draft for `query_id` into `core.escalations`, inserts the initial `PENDING` entry into `core.escalation_status_history`, updates `core.generation_diagnostics.outcome` to `ESCALATED`, removes the draft, and invokes the configured `EscalationDestination`.
   - Returns `410 Gone` if the confirmation window has expired, `404 Not Found` for an unknown `query_id`, and `400 Bad Request` for invalid or failed queries.

3. **Context included in every escalation record:**
   - `query_id` — correlation ID linking the escalation 1:1 to `core.generation_diagnostics`
   - `question_text` — original employee question
   - `requester_email` — employee corporate email
   - `source_references` — enriched RAG citations where available (`title`, `url`, `document_id`, `chunk_id`, `score`)
   - Correlated diagnostic metadata surfaced in `EscalationDetail`: `channel`, `reason_code`, `confidence_score`

## Configuration & Extending Destinations

Configured in `src/main/resources/application.yaml`:

```yaml
app:
  escalation:
    destination: ${ESCALATION_DESTINATION:database}
    draft-ttl: ${ESCALATION_DRAFT_TTL:15m}
```

- `app.escalation.destination` (`ESCALATION_DESTINATION`): selects the active `EscalationDestination` implementation by `destinationType()`. Defaults to `database` (`DatabaseQueueEscalationDestination`). Startup fails fast if an unknown destination name is configured.
- `app.escalation.draft-ttl` (`ESCALATION_DRAFT_TTL`): confirmation window before an unconfirmed draft expires (`410 Gone`). Defaults to `15m`.

To route confirmed escalations to an external ticketing system (e.g. Jira or ServiceNow) once approved, implement `EscalationDestination` as a Spring `@Component` with a unique `destinationType()` and set `ESCALATION_DESTINATION` to that type.
