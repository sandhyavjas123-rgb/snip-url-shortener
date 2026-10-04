package dev.snip.link;

import dev.snip.link.api.*;
import dev.snip.common.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@Tag(name = "Short links", description = "Create, resolve, manage, and inspect short URLs")
public class LinkController {
    private static final Logger log = LoggerFactory.getLogger(LinkController.class);
    private final LinkService links;
    private final AnalyticsService analytics;

    public LinkController(LinkService links, AnalyticsService analytics) {
        this.links = links;
        this.analytics = analytics;
    }

    @PostMapping("/api/links")
    @Operation(summary = "Create or reuse a short link",
            description = "Returns an existing active mapping when the normalized destination already exists; otherwise creates a generated or custom code.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Link created or active duplicate returned", content = @Content(schema = @Schema(implementation = LinkResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid URL, alias, or expiry", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Custom alias already exists", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Per-IP API limit exceeded", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(links.create(request));
    }

    @GetMapping("/api/links")
    @Operation(summary = "List the 25 most recent links", description = "Returns newest links first, including their current click count and active state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recent links", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LinkResponse.class)))),
            @ApiResponse(responseCode = "429", description = "Per-IP API limit exceeded", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    List<LinkResponse> list() { return links.list(); }

    @GetMapping("/api/links/{code}")
    @Operation(summary = "Get one short link", description = "Returns link metadata without redirecting or incrementing analytics.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Link metadata", content = @Content(schema = @Schema(implementation = LinkResponse.class))),
            @ApiResponse(responseCode = "404", description = "Code does not exist", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Per-IP API limit exceeded", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    LinkResponse get(@Parameter(description = "Short code", example = "launch") @PathVariable String code) { return links.get(code); }

    @PutMapping("/api/links/{code}")
    @Operation(summary = "Update a short link", description = "Replaces the destination and expiry while preserving the code. Clearing expiry reactivates an expired link.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated link", content = @Content(schema = @Schema(implementation = LinkResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid URL or expiry", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Code does not exist", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Per-IP API limit exceeded", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    LinkResponse update(@Parameter(description = "Short code", example = "launch") @PathVariable String code,
                        @Valid @RequestBody UpdateLinkRequest request) {
        return links.update(code, request);
    }

    @DeleteMapping("/api/links/{code}")
    @Operation(summary = "Delete a short link", description = "Permanently removes the link and cascades deletion to its complete click history.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Link and click history deleted"),
            @ApiResponse(responseCode = "404", description = "Code does not exist", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Per-IP API limit exceeded", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    ResponseEntity<Void> delete(@Parameter(description = "Short code", example = "launch") @PathVariable String code) {
        links.delete(code);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/links/{code}/analytics")
    @Operation(summary = "Get click analytics", description = "Returns totals, privacy-preserving unique visitors, country counts, and up to 20 recent clicks.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analytics snapshot", content = @Content(schema = @Schema(implementation = AnalyticsResponse.class))),
            @ApiResponse(responseCode = "404", description = "Code does not exist", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Per-IP API limit exceeded", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    AnalyticsResponse analytics(@Parameter(description = "Short code", example = "launch") @PathVariable String code) { return analytics.analytics(code); }

    @GetMapping("/r/{code}")
    @Operation(summary = "Redirect to the destination URL", description = "Resolves an active code, dispatches best-effort analytics, and returns the destination in the Location header.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Destination resolved", headers = @Header(name = "Location", description = "Absolute destination URL", schema = @Schema(type = "string", format = "uri"))),
            @ApiResponse(responseCode = "404", description = "Code is missing or inactive", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "410", description = "Link has expired", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    ResponseEntity<Void> redirect(@Parameter(description = "Short code", example = "launch") @PathVariable String code, HttpServletRequest request) {
        String destination = links.resolve(code);
        try { analytics.record(code, clientIp(request), request.getHeader("User-Agent"), request.getHeader("Referer")); }
        catch (RuntimeException ex) { log.warn("Analytics dispatch failed for {}: {}", code, ex.getMessage()); }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(destination)).build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",", 2)[0].trim();
    }
}

