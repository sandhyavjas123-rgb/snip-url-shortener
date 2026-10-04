package dev.snip.link;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.snip.common.GlobalExceptionHandler;
import dev.snip.common.NotFoundException;
import dev.snip.link.api.LinkResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LinkControllerTest {
    private LinkService links;
    private AnalyticsService analytics;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        links = mock(LinkService.class);
        analytics = mock(AnalyticsService.class);
        mvc = MockMvcBuilders.standaloneSetup(new LinkController(links, analytics))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void createReturnsCreatedResource() throws Exception {
        var response = new LinkResponse("hello", "http://localhost/r/hello", "https://example.com", Instant.now(), null, true, 0);
        when(links.create(any())).thenReturn(response);
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com\",\"customAlias\":\"hello\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("hello"));
    }

    @Test
    void validationErrorsUseConsistentContract() throws Exception {
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"\",\"customAlias\":\"not-valid!\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.url").exists()).andExpect(jsonPath("$.details.customAlias").exists());
    }

    @Test
    void redirectPublishesAnalyticsAndReturnsLocation() throws Exception {
        when(links.resolve("abc123")).thenReturn("https://example.com/story");
        mvc.perform(get("/r/abc123").header("X-Forwarded-For", "203.0.113.9").header("User-Agent", "test"))
                .andExpect(status().isFound()).andExpect(header().string("Location", "https://example.com/story"));
        verify(analytics).record(eq("abc123"), eq("203.0.113.9"), eq("test"), isNull());
    }

    @Test
    void notFoundUsesStructuredError() throws Exception {
        when(links.get("missing")).thenThrow(new NotFoundException("missing"));
        mvc.perform(get("/api/links/missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mvc.perform(delete("/api/links/abc")).andExpect(status().isNoContent());
        verify(links).delete("abc");
    }

    @Test
    void listReturnsLinks() throws Exception {
        when(links.list()).thenReturn(List.of());
        mvc.perform(get("/api/links")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }
}

