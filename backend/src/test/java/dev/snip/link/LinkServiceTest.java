package dev.snip.link;

import dev.snip.common.*;
import dev.snip.link.api.*;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LinkServiceTest {
    private ShortLinkRepository links;
    private ClickEventRepository clicks;
    private LinkCache cache;
    private LinkService service;
    private final Instant now = Instant.parse("2026-10-04T10:00:00Z");

    @BeforeEach
    void setUp() {
        links = mock(ShortLinkRepository.class);
        clicks = mock(ClickEventRepository.class);
        cache = mock(LinkCache.class);
        service = new LinkService(links, clicks, cache, Clock.fixed(now, ZoneOffset.UTC), "http://localhost:3000/");
        when(cache.code(anyString())).thenReturn(Optional.empty());
        when(cache.destination(anyString())).thenReturn(Optional.empty());
        when(clicks.countByShortLink(any())).thenReturn(0L);
    }

    @Test
    void createsCustomAliasAndCachesIt() {
        when(links.findFirstByDestinationUrlAndActiveTrueOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());
        when(links.existsByCode("go2026")).thenReturn(false);
        when(links.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LinkResponse result = service.create(new CreateLinkRequest("https://example.com/a/../story", "go2026", now.plusSeconds(3600)));

        assertEquals("go2026", result.code());
        assertEquals("https://example.com/story", result.destinationUrl());
        assertEquals("http://localhost:3000/r/go2026", result.shortUrl());
        verify(cache).put(any(ShortLink.class));
    }

    @Test
    void returnsExistingActiveLinkForDuplicateDestination() {
        ShortLink existing = new ShortLink("saved", "https://example.com", now.minusSeconds(30), null);
        when(cache.code("https://example.com")).thenReturn(Optional.of("saved"));
        when(links.findByCode("saved")).thenReturn(Optional.of(existing));

        LinkResponse result = service.create(new CreateLinkRequest("https://example.com", null, null));

        assertEquals("saved", result.code());
        verify(links, never()).saveAndFlush(any());
    }

    @Test
    void rejectsUnsafeSchemesAndEmbeddedCredentials() {
        assertThrows(IllegalArgumentException.class, () -> service.create(new CreateLinkRequest("javascript:alert(1)", null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(new CreateLinkRequest("https://user:pass@example.com", null, null)));
    }

    @Test
    void rejectsTakenAlias() {
        when(links.findFirstByDestinationUrlAndActiveTrueOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());
        when(links.existsByCode("taken")).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.create(new CreateLinkRequest("https://example.com", "taken", null)));
    }

    @Test
    void redirectsFromRedisWithoutDatabaseLookup() {
        when(cache.destination("fast")).thenReturn(Optional.of("https://example.com/cached"));
        assertEquals("https://example.com/cached", service.resolve("fast"));
        verify(links, never()).findByCode(anyString());
    }

    @Test
    void expiresAndDeactivatesStaleLink() {
        ShortLink expired = new ShortLink("old", "https://example.com", now.minusSeconds(100), now.minusSeconds(1));
        when(links.findByCode("old")).thenReturn(Optional.of(expired));
        assertThrows(ExpiredLinkException.class, () -> service.resolve("old"));
        assertFalse(expired.isActive());
        verify(cache).evict(expired);
    }

    @Test
    void updatePreservesCodeAndRefreshesCache() {
        ShortLink link = new ShortLink("same", "https://old.example", now, null);
        when(links.findByCode("same")).thenReturn(Optional.of(link));
        LinkResponse response = service.update("same", new UpdateLinkRequest("https://new.example/path", null));
        assertEquals("same", response.code());
        assertEquals("https://new.example/path", response.destinationUrl());
        verify(cache).evict(link);
        verify(cache).put(link);
    }

    @Test
    void deleteRemovesLinkAndCacheEntry() {
        ShortLink link = new ShortLink("gone", "https://example.com", now, null);
        when(links.findByCode("gone")).thenReturn(Optional.of(link));
        service.delete("gone");
        verify(cache).evict(link);
        verify(links).delete(link);
    }
}

