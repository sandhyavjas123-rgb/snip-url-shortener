CREATE TABLE short_links (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    destination_url VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE click_events (
    id BIGSERIAL PRIMARY KEY,
    short_link_id BIGINT NOT NULL REFERENCES short_links(id) ON DELETE CASCADE,
    visitor_hash VARCHAR(24) NOT NULL,
    country VARCHAR(96) NOT NULL,
    referrer_host VARCHAR(255),
    device_type VARCHAR(24) NOT NULL,
    clicked_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_short_links_created_at ON short_links(created_at DESC);
CREATE INDEX idx_click_events_link_time ON click_events(short_link_id, clicked_at DESC);

