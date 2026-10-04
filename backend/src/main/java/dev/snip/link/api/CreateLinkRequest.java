package dev.snip.link.api;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record CreateLinkRequest(
        @Schema(description = "Absolute HTTP or HTTPS destination", example = "https://example.com/guides/spring", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 2048) String url,
        @Schema(description = "Optional 1-8 character alphanumeric code", example = "spring", nullable = true)
        @Pattern(regexp = "^[A-Za-z0-9]{1,8}$", message = "must be 1 to 8 alphanumeric characters") String customAlias,
        @Schema(description = "Optional future UTC expiry; null means no expiry", example = "2027-01-01T00:00:00Z", nullable = true)
        @Future(message = "must be in the future") Instant expiresAt) {}

