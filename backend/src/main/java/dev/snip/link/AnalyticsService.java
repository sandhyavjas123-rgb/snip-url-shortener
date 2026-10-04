package dev.snip.link;

import dev.snip.link.api.AnalyticsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;

@Service
public class AnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private final ShortLinkRepository links;
    private final ClickEventRepository clicks;
    private final LinkService linkService;
    private final GeoIpService geoIp;

    public AnalyticsService(ShortLinkRepository links, ClickEventRepository clicks, LinkService linkService, GeoIpService geoIp) {
        this.links = links;
        this.clicks = clicks;
        this.linkService = linkService;
        this.geoIp = geoIp;
    }

    @Async
    @Transactional
    public void record(String code, String ip, String userAgent, String referrer) {
        try {
            ShortLink link = links.findByCode(code).orElse(null);
            if (link == null) return;
            clicks.save(new ClickEvent(link, visitorHash(ip), geoIp.countryFor(ip), referrerHost(referrer), device(userAgent), Instant.now()));
        } catch (RuntimeException ex) {
            log.warn("Analytics recording failed for {}: {}", code, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse analytics(String code) {
        ShortLink link = linkService.requireLink(code);
        LinkedHashMap<String, Long> countries = new LinkedHashMap<>();
        clicks.countCountries(link).forEach(row -> countries.put((String) row[0], (Long) row[1]));
        var recent = clicks.findByShortLinkOrderByClickedAtDesc(link, PageRequest.of(0, 20)).stream()
                .map(c -> new AnalyticsResponse.RecentClick(c.getCountry(), c.getDeviceType(), c.getReferrerHost(), c.getClickedAt())).toList();
        return new AnalyticsResponse(linkService.toResponse(link), clicks.countByShortLink(link), clicks.countUniqueVisitors(link), countries, recent);
    }

    private String visitorHash(String ip) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(ip.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest).substring(0, 24);
        } catch (Exception ex) { return "anonymous"; }
    }

    private String referrerHost(String value) {
        if (value == null || value.isBlank()) return "Direct";
        try { return URI.create(value).getHost() == null ? "Other" : URI.create(value).getHost(); }
        catch (Exception ex) { return "Other"; }
    }

    private String device(String ua) {
        String value = ua == null ? "" : ua.toLowerCase();
        if (value.contains("tablet") || value.contains("ipad")) return "Tablet";
        if (value.contains("mobile") || value.contains("android") || value.contains("iphone")) return "Mobile";
        if (value.contains("bot") || value.contains("crawler") || value.contains("spider")) return "Bot";
        return "Desktop";
    }
}
