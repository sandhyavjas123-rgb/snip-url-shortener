package dev.snip.link.api;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record UpdateLinkRequest(
        @Schema(description = "Replacement absolute HTTP or HTTPS destination", example = "https://example.org/new-destination", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 2048) String url,
        @Schema(description = "Replacement future expiry; null clears expiry and reactivates the link", example = "2027-06-30T12:00:00Z", nullable = true)
        @Future(message = "must be in the future") Instant expiresAt) {}

