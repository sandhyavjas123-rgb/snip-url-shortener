package dev.snip.link;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "click_events")
public class ClickEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "short_link_id", nullable = false)
    private ShortLink shortLink;
    @Column(name = "visitor_hash", nullable = false, length = 24)
    private String visitorHash;
    @Column(nullable = false, length = 96)
    private String country;
    @Column(name = "referrer_host", length = 255)
    private String referrerHost;
    @Column(name = "device_type", nullable = false, length = 24)
    private String deviceType;
    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    protected ClickEvent() {}

    public ClickEvent(ShortLink shortLink, String visitorHash, String country, String referrerHost, String deviceType, Instant clickedAt) {
        this.shortLink = shortLink;
        this.visitorHash = visitorHash;
        this.country = country;
        this.referrerHost = referrerHost;
        this.deviceType = deviceType;
        this.clickedAt = clickedAt;
    }

    public String getReferrerHost() { return referrerHost; }
    public String getCountry() { return country; }
    public String getDeviceType() { return deviceType; }
    public Instant getClickedAt() { return clickedAt; }
}

