package dev.snip.link;

import dev.snip.common.*;
import dev.snip.link.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.security.SecureRandom;
import java.time.*;
import java.util.List;

@Service
public class LinkService {
    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private final ShortLinkRepository links;
    private final ClickEventRepository clicks;
    private final LinkCache cache;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final String publicBaseUrl;

    @Autowired
    public LinkService(ShortLinkRepository links, ClickEventRepository clicks, LinkCache cache,
                       @Value("${app.public-base-url}") String publicBaseUrl) {
        this(links, clicks, cache, Clock.systemUTC(), publicBaseUrl);
    }

    LinkService(ShortLinkRepository links, ClickEventRepository clicks, LinkCache cache, Clock clock, String publicBaseUrl) {
        this.links = links;
        this.clicks = clicks;
        this.cache = cache;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public LinkResponse create(CreateLinkRequest request) {
        String url = normalizeUrl(request.url());
        Instant now = clock.instant();
        ShortLink duplicate = findActiveDuplicate(url, now);
        if (duplicate != null) return toResponse(duplicate);

        String code = request.customAlias() == null || request.customAlias().isBlank()
                ? uniqueRandomCode() : request.customAlias();
        if (links.existsByCode(code)) throw new ConflictException("That custom alias is already in use.");
        ShortLink link = new ShortLink(code, url, now, request.expiresAt());
        try { link = links.saveAndFlush(link); }
        catch (DataIntegrityViolationException ex) { throw new ConflictException("That alias was just claimed. Please choose another."); }
        cache.put(link);
        return toResponse(link);
    }

    @Transactional(readOnly = true)
    public List<LinkResponse> list() {
        return links.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 25)).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LinkResponse get(String code) { return toResponse(requireLink(code)); }

    @Transactional
    public LinkResponse update(String code, UpdateLinkRequest request) {
        ShortLink link = requireLink(code);
        cache.evict(link);
        link.update(normalizeUrl(request.url()), request.expiresAt());
        cache.put(link);
        return toResponse(link);
    }

    @Transactional
    public void delete(String code) {
        ShortLink link = requireLink(code);
        cache.evict(link);
        links.delete(link);
    }

    @Transactional
    public String resolve(String code) {
        var cached = cache.destination(code);
        if (cached.isPresent()) return cached.get();
        ShortLink link = requireLink(code);
        if (!link.isActive()) throw new NotFoundException("This short link is inactive.");
        if (link.isExpired(clock.instant())) {
            link.deactivate();
            cache.evict(link);
            throw new ExpiredLinkException("This short link has expired.");
        }
        cache.put(link);
        return link.getDestinationUrl();
    }

    ShortLink requireLink(String code) {
        return links.findByCode(code).orElseThrow(() -> new NotFoundException("Short link '" + code + "' was not found."));
    }

    private ShortLink findActiveDuplicate(String url, Instant now) {
        String cachedCode = cache.code(url).orElse(null);
        if (cachedCode != null) {
            ShortLink cached = links.findByCode(cachedCode).orElse(null);
            if (isReusable(cached, now)) return cached;
        }
        ShortLink found = links.findFirstByDestinationUrlAndActiveTrueOrderByCreatedAtDesc(url).orElse(null);
        if (isReusable(found, now)) { cache.put(found); return found; }
        if (found != null && found.isExpired(now)) found.deactivate();
        return null;
    }

    private boolean isReusable(ShortLink link, Instant now) { return link != null && link.isActive() && !link.isExpired(now); }

    private String uniqueRandomCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder(7);
            for (int i = 0; i < 7; i++) code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            if (!links.existsByCode(code.toString())) return code.toString();
        }
        throw new IllegalStateException("Unable to allocate a short code.");
    }

    private String normalizeUrl(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("URL is required.");
        try {
            URI uri = URI.create(raw.trim()).normalize();
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null)
                throw new IllegalArgumentException("URL must be an absolute HTTP or HTTPS address.");
            if (uri.getUserInfo() != null) throw new IllegalArgumentException("URLs containing embedded credentials are not allowed.");
            return uri.toASCIIString();
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && (ex.getMessage().startsWith("URL must") || ex.getMessage().startsWith("URLs containing"))) throw ex;
            throw new IllegalArgumentException("URL is not valid.");
        }
    }

    LinkResponse toResponse(ShortLink link) {
        return new LinkResponse(link.getCode(), publicBaseUrl + "/r/" + link.getCode(), link.getDestinationUrl(),
                link.getCreatedAt(), link.getExpiresAt(), link.isActive() && !link.isExpired(clock.instant()), clicks.countByShortLink(link));
    }
}

