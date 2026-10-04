package dev.snip.link;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class LinkCache {
    private static final Logger log = LoggerFactory.getLogger(LinkCache.class);
    private final StringRedisTemplate redis;
    private final Duration ttl;

    public LinkCache(StringRedisTemplate redis, @Value("${app.cache-ttl}") Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    public Optional<String> destination(String code) { return read("code:" + code); }
    public Optional<String> code(String url) { return read("url:" + url); }

    public void put(ShortLink link) {
        write(() -> {
            Duration effective = link.getExpiresAt() == null ? ttl : min(ttl, Duration.between(java.time.Instant.now(), link.getExpiresAt()));
            if (!effective.isNegative() && !effective.isZero()) {
                redis.opsForValue().set("code:" + link.getCode(), link.getDestinationUrl(), effective);
                redis.opsForValue().set("url:" + link.getDestinationUrl(), link.getCode(), effective);
            }
            return null;
        });
    }

    public void evict(ShortLink link) {
        write(() -> { redis.delete(java.util.List.of("code:" + link.getCode(), "url:" + link.getDestinationUrl())); return null; });
    }

    private Optional<String> read(String key) {
        try { return Optional.ofNullable(retry(() -> redis.opsForValue().get(key))); }
        catch (RuntimeException ex) { log.warn("Redis read failed after retries: {}", ex.getMessage()); return Optional.empty(); }
    }

    private void write(Supplier<Void> operation) {
        try { retry(operation); }
        catch (RuntimeException ex) { log.warn("Redis write failed after retries: {}", ex.getMessage()); }
    }

    private <T> T retry(Supplier<T> operation) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try { return operation.get(); }
            catch (RuntimeException ex) {
                last = ex;
                try { Thread.sleep(25L << attempt); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw ex; }
            }
        }
        throw last;
    }

    private Duration min(Duration a, Duration b) { return a.compareTo(b) < 0 ? a : b; }
}
