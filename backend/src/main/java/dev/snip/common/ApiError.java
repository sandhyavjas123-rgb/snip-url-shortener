package dev.snip.common;

import java.time.Instant;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

public record ApiError(
        @Schema(example = "2026-10-04T08:32:15Z") Instant timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "VALIDATION_ERROR") String code,
        @Schema(example = "Please correct the highlighted fields.") String message,
        @Schema(description = "Field-specific validation messages", example = "{\"customAlias\":\"must be 1 to 8 alphanumeric characters\"}") Map<String, String> details) {}

