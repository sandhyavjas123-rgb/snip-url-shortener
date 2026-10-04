package dev.snip.link.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

public record AnalyticsResponse(
        LinkResponse link,
        @Schema(example = "12") long totalClicks,
        @Schema(example = "8") long uniqueVisitors,
        @Schema(description = "Country name to click-count map", example = "{\"United States\":7,\"India\":5}") Map<String, Long> countries,
        List<RecentClick> recentClicks) {
    public record RecentClick(
            @Schema(example = "India") String country,
            @Schema(example = "Mobile") String device,
            @Schema(example = "example.com") String referrer,
            @Schema(example = "2026-10-04T08:31:00Z") Instant clickedAt) {}
}

