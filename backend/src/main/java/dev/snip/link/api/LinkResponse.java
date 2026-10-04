package dev.snip.link.api;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

public record LinkResponse(
        @Schema(example = "spring") String code,
        @Schema(example = "http://localhost:3000/r/spring") String shortUrl,
        @Schema(example = "https://example.com/guides/spring") String destinationUrl,
        @Schema(example = "2026-10-04T08:30:00Z") Instant createdAt,
        @Schema(example = "2027-01-01T00:00:00Z", nullable = true) Instant expiresAt,
        @Schema(example = "true") boolean active,
        @Schema(example = "12") long clicks) {}

