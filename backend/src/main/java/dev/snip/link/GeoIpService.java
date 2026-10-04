package dev.snip.link;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Map;

@Service
public class GeoIpService {
    private static final Logger log = LoggerFactory.getLogger(GeoIpService.class);
    private final RestClient client = RestClient.builder().build();
    private final StringRedisTemplate redis;
    private final Duration ttl;

    public GeoIpService(StringRedisTemplate redis, @Value("${app.geo-cache-ttl}") Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    public String countryFor(String ip) {
        if (isPrivate(ip)) return "Local / private";
        String key = "geoip:" + ip;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) return cached;
        } catch (RuntimeException ex) {
            log.debug("Geo cache read unavailable: {}", ex.getMessage());
        }

        String country = lookupWithRetries("https://ipwho.is/" + ip, "country");
        if (country == null) country = lookupWithRetries("http://ip-api.com/json/" + ip + "?fields=country", "country");
        if (country == null || country.isBlank()) country = "Unknown";
        try { redis.opsForValue().set(key, country, ttl); }
        catch (RuntimeException ex) { log.debug("Geo cache write unavailable: {}", ex.getMessage()); }
        return country;
    }

    @SuppressWarnings("unchecked")
    private String lookupWithRetries(String url, String field) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Map<String, Object> response = client.get().uri(url).retrieve().body(Map.class);
                Object value = response == null ? null : response.get(field);
                return value instanceof String text && !text.isBlank() ? text : null;
            } catch (RuntimeException ex) {
                if (attempt == 2) log.info("Geo provider unavailable: {}", ex.getMessage());
                else backoff(attempt);
            }
        }
        return null;
    }

    private boolean isPrivate(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress();
        } catch (Exception ex) { return true; }
    }

    private void backoff(int attempt) {
        try { Thread.sleep(100L << attempt); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
}

