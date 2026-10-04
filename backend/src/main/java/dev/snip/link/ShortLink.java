package dev.snip.link;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "short_links")
public class ShortLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 32)
    private String code;
    @Column(name = "destination_url", nullable = false, length = 2048)
    private String destinationUrl;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(nullable = false)
    private boolean active;

    protected ShortLink() {}

    public ShortLink(String code, String destinationUrl, Instant createdAt, Instant expiresAt) {
        this.code = code;
        this.destinationUrl = destinationUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.active = true;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getDestinationUrl() { return destinationUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isActive() { return active; }
    public void deactivate() { this.active = false; }
    public void update(String destinationUrl, Instant expiresAt) {
        this.destinationUrl = destinationUrl;
        this.expiresAt = expiresAt;
        this.active = true;
    }
    public boolean isExpired(Instant now) { return expiresAt != null && !expiresAt.isAfter(now); }
}

