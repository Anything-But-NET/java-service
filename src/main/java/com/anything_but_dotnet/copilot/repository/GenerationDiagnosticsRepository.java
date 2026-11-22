package com.anything_but_dotnet.copilot.repository;

import com.anything_but_dotnet.copilot.entity.GenerationDiagnostics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GenerationDiagnosticsRepository
        extends JpaRepository<GenerationDiagnostics, UUID>, JpaSpecificationExecutor<GenerationDiagnostics> {
    Optional<GenerationDiagnostics> findFirstByQueryId(String queryId);
    boolean existsByQueryId(String queryId);

    @Query(
            nativeQuery = true,
            value = """
WITH filtered AS (
    SELECT *
    FROM core.generation_diagnostics
    WHERE (CAST(:from AS TIMESTAMPTZ) IS NULL OR created_at >= CAST(:from AS TIMESTAMPTZ))
      AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR created_at <= CAST(:to AS TIMESTAMPTZ))
      AND (CAST(:channel AS TEXT) IS NULL OR channel::text = CAST(:channel AS TEXT))
      AND (CAST(:prompt_version_id AS UUID) IS NULL OR prompt_version_id = CAST(:prompt_version_id AS UUID))
),
daily_outcomes AS (
    SELECT (created_at AT TIME ZONE 'UTC')::date AS day,
           outcome,
           count(*) AS cnt
    FROM filtered
    GROUP BY 1, 2
),
daily_agg AS (
    SELECT day,
           sum(cnt)::bigint AS total_questions,
           jsonb_agg(
               jsonb_build_object('outcome', outcome, 'count', cnt)
               ORDER BY outcome
           ) AS outcome_counts
    FROM daily_outcomes
    GROUP BY day
),
result AS (
    SELECT count(*) AS total_questions,
           COALESCE(
               (
                   SELECT jsonb_agg(
                       jsonb_build_object('outcome', outcome, 'count', cnt)
                       ORDER BY outcome
                   )
                   FROM (
                       SELECT outcome, count(*) AS cnt
                       FROM filtered
                       GROUP BY outcome
                   ) t
               ), '[]'::jsonb
           ) AS outcome_counts,
           avg(confidence_score) AS avg_confidence_score,
           jsonb_build_object(
               'high', count(*) FILTER (WHERE confidence_score >= 0.7),
               'medium', count(*) FILTER (WHERE confidence_score >= 0.4 AND confidence_score < 0.7),
               'low', count(*) FILTER (WHERE confidence_score < 0.4)
           ) AS confidence_buckets,
           avg(latency_ms)::double precision AS avg_latency_ms,
           percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) AS p95_latency_ms,
           COALESCE(
               (
                   SELECT jsonb_agg(
                       jsonb_build_object('reason_code', reason_code, 'count', cnt)
                       ORDER BY reason_code
                   )
                   FROM (
                       SELECT reason_code, count(*) AS cnt
                       FROM filtered
                       WHERE reason_code IS NOT NULL
                       GROUP BY reason_code
                   ) t
               ), '[]'::jsonb
           ) AS reason_code_counts,
           COALESCE(
               (
                   SELECT jsonb_agg(
                       jsonb_build_object('error_category', error_category, 'count', cnt)
                       ORDER BY error_category
                   )
                   FROM (
                       SELECT error_category, count(*) AS cnt
                       FROM filtered
                       WHERE error_category IS NOT NULL
                       GROUP BY error_category
                   ) t
               ), '[]'::jsonb
           ) AS error_category_counts,
           COALESCE(
               (
                   SELECT jsonb_agg(
                       jsonb_build_object('channel', channel, 'count', cnt)
                       ORDER BY channel
                   )
                   FROM (
                       SELECT channel, count(*) AS cnt
                       FROM filtered
                       GROUP BY channel
                   ) t
               ), '[]'::jsonb
           ) AS channel_counts,
           COALESCE(
               (
                   SELECT jsonb_agg(
                       jsonb_build_object(
                           'date', day,
                           'total_questions', total_questions,
                           'outcome_counts', outcome_counts
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
            @Param("channel") String channel,
            @Param("prompt_version_id") UUID promptVersionId
    );
}
