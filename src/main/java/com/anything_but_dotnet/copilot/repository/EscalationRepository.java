package com.anything_but_dotnet.copilot.repository;

import com.anything_but_dotnet.copilot.entity.Escalation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EscalationRepository
        extends JpaRepository<Escalation, UUID>, JpaSpecificationExecutor<Escalation> {
    Optional<Escalation> findByQueryId(String queryId);
    List<Escalation> findByQueryIdIn(Collection<String> queryIds);

    @Query(
            nativeQuery = true,
            value = """
WITH filtered AS (
    SELECT e.status,
           e.created_at,
           e.resolved_at,
           CASE
               WHEN e.resolved_at IS NOT NULL
               THEN GREATEST(0.0, EXTRACT(EPOCH FROM (e.resolved_at - e.created_at)) * 1000.0)
               ELSE NULL
           END AS resolution_time_ms
    FROM core.escalations e
    LEFT JOIN core.generation_diagnostics d ON d.query_id = e.query_id
    WHERE (CAST(:from AS TIMESTAMPTZ) IS NULL OR e.created_at >= CAST(:from AS TIMESTAMPTZ))
      AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR e.created_at <= CAST(:to AS TIMESTAMPTZ))
      AND (CAST(:channel AS TEXT) IS NULL OR d.channel::text = CAST(:channel AS TEXT))
),
daily_agg AS (
    SELECT (created_at AT TIME ZONE 'UTC')::date AS day,
           count(*)::bigint AS total_escalations,
           count(*) FILTER (WHERE resolved_at IS NOT NULL)::bigint AS resolved_count
    FROM filtered
    GROUP BY 1
),
result AS (
    SELECT count(*) AS total_escalations,
           COALESCE(
               (
                   SELECT jsonb_agg(
                       jsonb_build_object('status', status, 'count', cnt)
                       ORDER BY status
                   )
                   FROM (
                       SELECT status, count(*) AS cnt
                       FROM filtered
                       GROUP BY status
                   ) t
               ), '[]'::jsonb
           ) AS status_counts,
           count(*) FILTER (WHERE resolved_at IS NOT NULL) AS resolved_count,
           CASE
               WHEN count(*) = 0 THEN NULL
               ELSE (count(*) FILTER (WHERE resolved_at IS NOT NULL))::double precision / count(*)::double precision
           END AS resolution_rate,
           avg(resolution_time_ms)::double precision AS avg_resolution_time_ms,
           percentile_cont(0.95) WITHIN GROUP (ORDER BY resolution_time_ms) AS p95_resolution_time_ms,
           COALESCE(
               (
                   SELECT jsonb_agg(
                       jsonb_build_object(
                           'date', day,
                           'total_escalations', total_escalations,
                           'resolved_count', resolved_count
                       )
                       ORDER BY day
                   )
                   FROM daily_agg
               ), '[]'::jsonb
           ) AS daily_trend
    FROM filtered
)
SELECT row_to_json(result)::text
FROM result;
"""
    )
    String getAnalyticsJson(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("channel") String channel
    );
}