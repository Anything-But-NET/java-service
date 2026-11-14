package com.anything_but_dotnet.copilot.dto.dashboard;

import com.anything_but_dotnet.copilot.model.DashboardErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * Shared envelope DTOs for Web Dashboard endpoints (pagination and standardized errors).
 */
public interface DashboardCommonDto {

    /**
     * Generic paginated response matching OpenAPI {@code *Page} schemas.
     */
    record PageResponse<T>(
            @NotNull
            @Valid
            @JsonProperty("items")
            List<T> items,

            @Min(0)
            @JsonProperty("page")
            int page,

            @Min(1)
            @JsonProperty("size")
            int size,

            @Min(0)
            @JsonProperty("total_elements")
            long totalElements,

            @Min(0)
            @JsonProperty("total_pages")
            int totalPages
    ) {
        public PageResponse {
            items = items != null ? List.copyOf(items) : List.of();
        }

        public static <T> PageResponse<T> from(Page<T> springPage) {
            return new PageResponse<>(
                    springPage.getContent(),
                    springPage.getNumber(),
                    Math.max(1, springPage.getSize()),
                    springPage.getTotalElements(),
                    springPage.getTotalPages()
            );
        }

        public static <E, T> PageResponse<T> from(Page<E> springPage, Function<E, T> mapper) {
            return from(springPage.map(mapper));
        }
    }

    /**
     * Standardized error payload matching OpenAPI {@code DashboardErrorResponse}.
     */
    record ErrorResponse(
            @NotNull
            @JsonProperty("error_code")
            DashboardErrorCode errorCode,

            @NotBlank
            @JsonProperty("message")
            String message,

            @NotNull
            @JsonProperty("timestamp")
            Instant timestamp
    ) {
        public static ErrorResponse of(DashboardErrorCode errorCode, String message, Instant timestamp) {
            return new ErrorResponse(errorCode, message, timestamp);
        }
    }
}
