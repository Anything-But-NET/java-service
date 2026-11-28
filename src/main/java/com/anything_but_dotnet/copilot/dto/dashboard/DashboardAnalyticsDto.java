package com.anything_but_dotnet.copilot.dto.dashboard;

import com.anything_but_dotnet.copilot.model.ChatChannel;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.EscalationStatus;
import com.anything_but_dotnet.copilot.model.GenerationOutcome;
import com.anything_but_dotnet.copilot.model.QuestionOutcomeReasonCode;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Aggregated analytics DTOs for {@code /api/v1/dash/analytics/*}.
 */
public interface DashboardAnalyticsDto {

    record OutcomeCount(
            @JsonProperty("outcome") GenerationOutcome outcome,
            @JsonProperty("count") long count
    ) {}

    record ReasonCodeCount(
            @JsonProperty("reason_code") QuestionOutcomeReasonCode reasonCode,
            @JsonProperty("count") long count
    ) {}

    record ErrorCategoryCount(
            @JsonProperty("error_category") ErrorCategory errorCategory,
            @JsonProperty("count") long count
    ) {}

    record ChannelCount(
            @JsonProperty("channel") ChatChannel channel,
            @JsonProperty("count") long count
    ) {}

    record ConfidenceBuckets(
            @JsonProperty("high") long high,
            @JsonProperty("medium") long medium,
            @JsonProperty("low") long low
    ) {
        public static ConfidenceBuckets empty() {
            return new ConfidenceBuckets(0L, 0L, 0L);
        }
    }

    record DailyOutcomePoint(
            @JsonProperty("date") LocalDate date,
            @JsonProperty("total_questions") long totalQuestions,
            @JsonProperty("outcome_counts") List<OutcomeCount> outcomeCounts
    ) {}

    record QuestionOutcomesAnalyticsResponse(
            @JsonProperty("from") Instant from,
            @JsonProperty("to") Instant to,
            @JsonProperty("total_questions") long totalQuestions,
            @JsonProperty("outcome_counts") List<OutcomeCount> outcomeCounts,
            @JsonProperty("avg_confidence_score") Double avgConfidenceScore,
            @JsonProperty("confidence_buckets") ConfidenceBuckets confidenceBuckets,
            @JsonProperty("avg_latency_ms") Double avgLatencyMs,
            @JsonProperty("p95_latency_ms") Double p95LatencyMs,
            @JsonProperty("reason_code_counts") List<ReasonCodeCount> reasonCodeCounts,
            @JsonProperty("error_category_counts") List<ErrorCategoryCount> errorCategoryCounts,
            @JsonProperty("channel_counts") List<ChannelCount> channelCounts,
            @JsonProperty("daily_trend") List<DailyOutcomePoint> dailyTrend
    ) {
        public static QuestionOutcomesAnalyticsResponse empty(Instant from, Instant to) {
            return new QuestionOutcomesAnalyticsResponse(
                    from,
                    to,
                    0L,
                    List.of(),
                    null,
                    ConfidenceBuckets.empty(),
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        public QuestionOutcomesAnalyticsResponse withTimeRange(Instant from, Instant to) {
            return new QuestionOutcomesAnalyticsResponse(
                    from,
                    to,
                    totalQuestions,
                    outcomeCounts != null ? outcomeCounts : List.of(),
                    avgConfidenceScore,
                    confidenceBuckets != null ? confidenceBuckets : ConfidenceBuckets.empty(),
                    avgLatencyMs,
                    p95LatencyMs,
                    reasonCodeCounts != null ? reasonCodeCounts : List.of(),
                    errorCategoryCounts != null ? errorCategoryCounts : List.of(),
                    channelCounts != null ? channelCounts : List.of(),
                    dailyTrend != null ? dailyTrend : List.of()
            );
        }
    }

    record EscalationStatusCount(
            @JsonProperty("status") EscalationStatus status,
            @JsonProperty("count") long count
    ) {}

    record DailyEscalationPoint(
            @JsonProperty("date") LocalDate date,
            @JsonProperty("total_escalations") long totalEscalations,
            @JsonProperty("resolved_count") long resolvedCount
    ) {}

    record EscalationAnalyticsResponse(
            @JsonProperty("from") Instant from,
            @JsonProperty("to") Instant to,
            @JsonProperty("total_escalations") long totalEscalations,
            @JsonProperty("status_counts") List<EscalationStatusCount> statusCounts,
            @JsonProperty("resolved_count") long resolvedCount,
            @JsonProperty("resolution_rate") Double resolutionRate,
            @JsonProperty("avg_resolution_time_ms") Double avgResolutionTimeMs,
            @JsonProperty("p95_resolution_time_ms") Double p95ResolutionTimeMs,
            @JsonProperty("daily_trend") List<DailyEscalationPoint> dailyTrend
    ) {
        public static EscalationAnalyticsResponse empty(Instant from, Instant to) {
            return new EscalationAnalyticsResponse(
                    from,
                    to,
                    0L,
                    List.of(),
                    0L,
                    null,
                    null,
                    null,
                    List.of()
            );
        }

        public EscalationAnalyticsResponse withTimeRange(Instant from, Instant to) {
            return new EscalationAnalyticsResponse(
                    from,
                    to,
                    totalEscalations,
                    statusCounts != null ? statusCounts : List.of(),
                    resolvedCount,
                    resolutionRate,
                    avgResolutionTimeMs,
                    p95ResolutionTimeMs,
                    dailyTrend != null ? dailyTrend : List.of()
            );
        }
    }
}
