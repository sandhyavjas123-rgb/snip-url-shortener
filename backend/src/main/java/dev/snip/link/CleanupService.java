package dev.snip.link;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class CleanupService {
    private final ShortLinkRepository links;
    private final LinkCache cache;

    public CleanupService(ShortLinkRepository links, LinkCache cache) {
        this.links = links;
        this.cache = cache;
    }

    @Scheduled(cron = "${app.cleanup-cron}")
    @Transactional
    public void removeExpiredLinks() {
        var expired = links.findByActiveTrueAndExpiresAtBefore(Instant.now());
        expired.forEach(cache::evict);
        links.deleteAll(expired);
    }
}

