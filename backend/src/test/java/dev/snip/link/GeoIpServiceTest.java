package dev.snip.link;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class GeoIpServiceTest {
    @Test
    void privateAddressesNeverCallExternalProviders() {
        GeoIpService service = new GeoIpService(mock(StringRedisTemplate.class), java.time.Duration.ofHours(24));
        assertEquals("Local / private", service.countryFor("127.0.0.1"));
        assertEquals("Local / private", service.countryFor("192.168.1.2"));
    }
}

