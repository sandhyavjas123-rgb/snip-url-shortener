<div align="center">

# Snip URL Shortener

### Short links. Clear signals. One command.

A production-minded URL shortener built with **Java 21**, **Spring Boot**, **React**, **PostgreSQL**,
and **Redis**. Create branded links, manage expiry, inspect click activity, and monitor the runtime
from one containerized workspace.

![Java 21](https://img.shields.io/badge/Java-21-ef7b45?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6db33f?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-202c38?logo=react)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169e1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-dc382d?logo=redis&logoColor=white)
![Tests](https://img.shields.io/badge/tests-20_passing-2e9b68)
![Docker](https://img.shields.io/badge/run-Docker_Compose-2496ed?logo=docker&logoColor=white)

[Quick start](#quick-start) · [Features](#feature-tour) · [Architecture](#architecture) ·
[Flows](#interactive-flows) · [API](#rest-api) · [Testing](#testing-and-quality-gates)

</div>

---

![Snip dashboard showing the shortening form and managed links](docs/screenshots/dashboard.png)

## Why this project exists

Snip demonstrates how a compact product can still have deliberate production qualities: clear API
contracts, durable storage, cache fallbacks, bounded retries, privacy-aware analytics, operational
metrics, automated tests, and a reproducible runtime.

It focuses on the URL-shortening product. Cloud deployment and Kafka are intentionally outside this
version; the consequences of those choices are documented rather than hidden.

## At a glance

| Capability | Implementation | Result |
|---|---|---|
| Shortening | SecureRandom Base62, seven characters | Shareable `/r/{code}` URLs |
| Custom aliases | 1-8 alphanumeric characters | Memorable links such as `/r/launch` |
| Duplicate detection | Redis fast path + PostgreSQL authority | Active destinations reuse their code |
| Expiration | Database timestamp + bounded cache TTL | Expired links return `410 Gone` |
| Management | Read, update, and delete APIs | Destination and expiry can change in place |
| Analytics | Asynchronous click persistence | Clicks, visitors, countries, devices, referrers |
| Reliability | Retry, fallback, health gates | Enrichment failure never changes a redirect |
| Abuse control | Bucket4j per-IP API limit | Predictable `429` error contract |
| Observability | Actuator, Prometheus, Grafana | JVM, HTTP, latency, and pool visibility |
| Delivery | Multi-stage images + Compose | Entire stack starts with one command |

## Feature tour

### Create links without leaving the workspace

Paste an HTTP or HTTPS destination and select **Shorten link**. Advanced options expose a custom
alias and optional expiry without cluttering the normal workflow.

**1. Enter a destination and, if useful, a custom alias:**

![A real Snip session with a long Spring Boot URL and custom alias ready to shorten](docs/screenshots/long-url-entered.png)

**2. Generate the short URL and manage it immediately from the workspace:**

![The same real Snip session after generating the guide26 short URL](docs/screenshots/short-url-generated.png)

- Generated codes use a cryptographically strong random source.
- Database uniqueness closes concurrent code-allocation races.
- An existing active destination is returned rather than duplicated.
- Unsafe schemes, missing hosts, and embedded credentials are rejected.

### Manage every link in context

The dashboard lists the 25 newest links with destination, creation time, activity status, and click
count. From the same row a user can copy, edit, delete, or open analytics. The compact
**Refresh stats** control beside **Your links** reloads click totals and any open analytics panel
without a full-page refresh.

- Updating preserves the public short code and can reactivate an expired link.
- Deleting cascades through the complete click history.
- Cache entries are evicted and rebuilt around mutations.

### Understand link activity

![Analytics detail with click totals, countries, devices, and recent activity](docs/screenshots/analytics.png)

Analytics includes total clicks, unique visitors, country distribution, device classification,
referrer host, and the 20 newest clicks. Raw visitor IP addresses are **not persisted**. A truncated
SHA-256 digest is stored only to calculate unique visitors, and private addresses never leave the
application.

### Operate the service

| Interface | URL after startup | Exposure |
|---|---|---|
| React workspace | [localhost:3000](http://localhost:3000) | Host port `3000` |
| Swagger UI | [localhost:3000/swagger-ui/index.html](http://localhost:3000/swagger-ui/index.html) | nginx proxy |
| OpenAPI JSON | [localhost:3000/v3/api-docs](http://localhost:3000/v3/api-docs) | nginx proxy |
| Health | [localhost:3000/actuator/health](http://localhost:3000/actuator/health) | Aggregate health only |
| Grafana | [localhost:3001](http://localhost:3001) | Host port `3001` |
| PostgreSQL / Redis / Prometheus / API | Compose network | No host port |

## Quick start

### Prerequisite

Install Docker Desktop, or Docker Engine with the Compose plugin. Java, Maven, Node.js, PostgreSQL,
and Redis do not need to be installed on the host.

```bash
docker compose up --build -d
```

That command builds both applications, runs the Java and React tests, waits for PostgreSQL, Redis,
and the API to become healthy, applies Flyway migrations, and starts nginx, Prometheus, and Grafana.

```bash
# View health and state
docker compose ps

# Follow application logs
docker compose logs -f backend web

# Stop while retaining data
docker compose down

# Stop and delete local data volumes
docker compose down -v
```

> [!IMPORTANT]
> Grafana defaults to `admin` / `snip-admin` locally. Override `GRAFANA_PASSWORD` before sharing a
> running environment.

## Architecture

### Container topology

```mermaid
flowchart TB
    User([Browser / API client])
    subgraph Public[Host-accessible edge]
        Web[React + nginx<br/>:3000]
        Grafana[Grafana<br/>:3001]
    end
    subgraph Private[Private Docker network]
        API[Spring Boot API<br/>:8080]
        Redis[(Redis 7<br/>cache)]
        DB[(PostgreSQL 16<br/>source of truth)]
        Prom[Prometheus<br/>metrics]
    end
    GeoA[ipwho.is]
    GeoB[ip-api.com]

    User --> Web
    User --> Grafana
    Web -->|API, redirects, docs, health| API
    API <--> Redis
    API <--> DB
    API -.->|primary| GeoA
    API -.->|fallback| GeoB
    Prom -->|scrape /actuator/prometheus| API
    Grafana -->|PromQL| Prom

    classDef edge fill:#e8f4ed,stroke:#176b51,color:#17231f;
    classDef app fill:#fff4ec,stroke:#e76947,color:#17231f;
    classDef store fill:#eef1ff,stroke:#5068b8,color:#17231f;
    class Web,Grafana edge;
    class API app;
    class Redis,DB,Prom store;
```

### Application layers

```mermaid
flowchart LR
    Controller[LinkController<br/>HTTP contract]
    Link[LinkService<br/>business rules]
    Analytics[AnalyticsService<br/>aggregation + async writes]
    Cache[LinkCache<br/>bounded Redis access]
    Geo[GeoIpService<br/>retry + fallback]
    Repositories[JPA repositories]
    Cleanup[CleanupService<br/>hourly expiry]

    Controller --> Link
    Controller -.-> Analytics
    Link --> Cache
    Link --> Repositories
    Analytics --> Geo
    Analytics --> Repositories
    Geo --> Cache
    Cleanup --> Cache
    Cleanup --> Repositories
```

PostgreSQL owns durable state and uniqueness. Redis is an optimization, never the authority. Cache
errors fall through to PostgreSQL; database constraints resolve races an application-only check
cannot prevent.

### Architectural principles

The implementation follows a few explicit rules that keep the system understandable under both
normal and degraded conditions:

1. **One authoritative store.** PostgreSQL decides whether a link exists, is unique, is active, and
   owns its click history. Redis can accelerate a decision but cannot permanently make one.
2. **Keep redirects narrow.** Resolving a code requires only a cache/database read and a `302`
   response. Analytics enrichment is dispatched after the destination is known.
3. **Bound every optional dependency.** Redis and GeoIP operations retry only a fixed number of
   times. Optional infrastructure cannot wait forever or recursively retry.
4. **Prefer database invariants over timing assumptions.** The unique `short_links.code` constraint
   is the final protection against concurrent alias or generated-code collisions.
5. **Make failure visible.** Health endpoints, structured errors, logs, Prometheus metrics, and the
   Grafana dashboard expose different views of the runtime.
6. **Keep deployment reproducible.** Build tools and runtime services live in containers; Compose
   expresses startup ordering and health dependencies.

### Component responsibilities

| Component | Responsibility | Does not own |
|---|---|---|
| React application | Forms, link table, edit/delete interactions, analytics presentation | Business validation or persistence |
| nginx | Static SPA delivery and reverse proxy for API, redirects, docs, and health | Application authorization or data |
| `LinkController` | HTTP routes, status codes, client metadata, DTO boundary | Link business rules |
| `LinkService` | Normalize URLs, detect duplicates, allocate codes, resolve, update, delete | GeoIP or click aggregation |
| `LinkCache` | Bidirectional Redis keys, TTL calculation, three-attempt operations | Authoritative existence or uniqueness |
| `AnalyticsService` | Dispatch/persist clicks and assemble analytics responses | Redirect success |
| `GeoIpService` | Private-IP detection, provider retries/fallback, country cache | Durable visitor identity |
| `CleanupService` | Hourly removal of expired links and cache entries | Request-time expiry enforcement |
| JPA repositories | Persistence queries and aggregates | HTTP or retry policy |
| Flyway | Ordered, repeatable schema evolution | Runtime object mapping |
| Prometheus/Grafana | Metrics storage, queries, and visualization | Application logs or tracing |

### Code allocation and concurrency

Generated codes contain seven characters selected from 62 symbols, providing `62^7` possible
values. `SecureRandom` supplies each character. Before insertion the service checks for an existing
code, but that check alone is not considered concurrency-safe. The database unique constraint is
the commit-time arbiter; if another request wins the race, the loser receives a controlled conflict
instead of overwriting data.

Custom aliases use the same constraint and accept only 1-8 ASCII alphanumeric characters. The
restricted alphabet keeps URLs portable and eliminates path delimiter ambiguity.

### Transaction and consistency boundaries

- Create, resolve-on-expiry, update, delete, cleanup, and click persistence run inside Spring
  transactions where state changes occur.
- Deleting a short link removes click events through the database foreign key's `ON DELETE CASCADE`.
- Update evicts the old destination/code pair before warming the new pair.
- Duplicate detection first checks `url:{destination}`, then confirms the record in PostgreSQL.
- An expired database match is deactivated and is never reused as a duplicate.
- Listing and analytics queries are read-only transactions.

This design provides strong durable consistency with eventually refreshed cache state. A brief
cache miss costs a database query; a stale cache entry cannot create a second durable truth.

### Cache behavior in detail

Each active link is cached in both directions. The normal maximum TTL is five minutes, but a link
that expires sooner receives only the remaining lifetime. Therefore Redis cannot intentionally
serve a mapping after its database expiry.

Redis access uses three attempts with short exponential backoff. When all reads fail, the operation
returns a cache miss and continues against PostgreSQL. Failed writes are logged and ignored because
the durable transaction has already established the correct state. This is graceful degradation,
not Redis-based database failover.

### Analytics execution model

`LinkController` resolves the destination first and then calls the Spring `@Async` analytics
service. The worker:

1. reloads the owning link;
2. hashes the source IP for unique-visitor calculation;
3. classifies the user agent;
4. extracts only the referrer host;
5. resolves a country from cache, primary provider, or fallback provider;
6. inserts the click event in its own transaction.

An analytics dispatch or worker exception is logged and does not replace a valid redirect. This is
low-latency best effort, not durable event delivery. A broker would be required for replay and
at-least-once semantics.

### Network and startup boundaries

Only nginx (`3000`) and Grafana (`3001`) publish host ports. PostgreSQL, Redis, Prometheus, and the
API communicate on the private Compose network. Compose starts the API only after database and
cache health checks pass, and starts nginx/Prometheus only after the API reports healthy.

The public nginx configuration intentionally proxies aggregate health but not raw Prometheus or
Actuator metric endpoints. Swagger and OpenAPI are available for local evaluation.

### Security and privacy boundaries

- Destination input must be an absolute `http` or `https` URI with a host.
- Embedded user-info credentials are rejected.
- The service stores a one-way visitor digest, never a raw IP address.
- Private, loopback, site-local, and link-local addresses never reach GeoIP providers.
- API management routes are limited to 20 requests/minute per source IP by default.
- Unexpected exceptions return a generic message instead of stack traces or internal details.
- The service never fetches destination content, avoiding a direct server-side request-forgery path.

Authentication, tenant ownership, TLS, trusted-proxy enforcement, and managed secrets remain
required before an internet-facing production deployment.

## Interactive flows

Expand one path at a time to review its decisions and failure boundaries.

<details>
<summary><strong>Flow A - Create or reuse a short link</strong></summary>

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UI as React UI
    participant API as Spring Boot
    participant Cache as Redis
    participant DB as PostgreSQL

    User->>UI: Submit destination, alias, expiry
    UI->>API: POST /api/links
    API->>API: Normalize and validate URL
    API->>Cache: GET url:{destination}
    alt reusable code cached
        Cache-->>API: short code
        API->>DB: Confirm authoritative record
    else cache miss or stale value
        API->>DB: Find active destination
        alt no duplicate
            API->>DB: Insert generated/custom code
        end
        API->>Cache: SET both directions
    end
    API-->>UI: 201 LinkResponse
    UI-->>User: Result + refreshed workspace
```

Redis duplicates are confirmed in PostgreSQL because the cache may be stale; the database decides
whether a mapping is active and unexpired.

</details>

<details>
<summary><strong>Flow B - Redirect and collect analytics</strong></summary>

```mermaid
sequenceDiagram
    autonumber
    actor Visitor
    participant API as Spring Boot
    participant Cache as Redis
    participant DB as PostgreSQL
    participant Worker as Async analytics
    participant Geo as GeoIP providers

    Visitor->>API: GET /r/{code}
    API->>Cache: GET code:{code}
    alt cache hit
        Cache-->>API: destination
    else cache miss
        API->>DB: Load and validate link
        DB-->>API: active destination
        API->>Cache: Restore bounded-TTL mapping
    end
    API-->>Visitor: 302 Location: destination
    API-->>Worker: Record click asynchronously
    Worker->>Cache: GET geoip:{ip}
    opt GeoIP cache miss
        Worker->>Geo: Primary then fallback with retries
        Worker->>Cache: Cache country for 24 hours
    end
    Worker->>DB: Insert click event
```

Redirect delivery does not wait for country resolution or the click insert. Analytics failure is
logged without changing the redirect response.

</details>

<details>
<summary><strong>Flow C - Update, delete, and expire</strong></summary>

```mermaid
flowchart LR
    Update[PUT /api/links/code] --> EvictOld[Evict old keys]
    EvictOld --> Persist[Update + reactivate]
    Persist --> Warm[Warm new keys]
    Delete[DELETE /api/links/code] --> EvictDelete[Evict keys]
    EvictDelete --> Cascade[Delete link + click history]
    Resolve[GET /r/code] --> Expired{Expired?}
    Expired -->|yes| Gone[Deactivate + evict<br/>410 Gone]
    Expired -->|no| Redirect[302 Found]
    Scheduler[Hourly cleanup] --> Remove[Evict + delete expired rows]
```

</details>

<details>
<summary><strong>Flow D - Observe application health</strong></summary>

```mermaid
sequenceDiagram
    participant API as Spring Boot + Micrometer
    participant Prom as Prometheus
    participant Grafana
    participant Operator
    Prom->>API: GET /actuator/prometheus every 15s
    API-->>Prom: HTTP, JVM, CPU, Hikari metrics
    Operator->>Grafana: Open Snip API Overview
    Grafana->>Prom: PromQL queries
    Prom-->>Grafana: Time-series results
    Grafana-->>Operator: Rate, heap, p95, DB pool
```

</details>

## Data and cache model

```mermaid
erDiagram
    SHORT_LINKS ||--o{ CLICK_EVENTS : "owns (cascade delete)"
    SHORT_LINKS {
        bigint id PK
        varchar code UK
        varchar destination_url
        timestamptz created_at
        timestamptz expires_at
        boolean active
    }
    CLICK_EVENTS {
        bigint id PK
        bigint short_link_id FK
        varchar visitor_hash
        varchar country
        varchar referrer_host
        varchar device_type
        timestamptz clicked_at
    }
```

| Redis key | Value | TTL | Purpose |
|---|---|---|---|
| `code:{shortCode}` | Destination URL | ≤ 5 minutes; never beyond expiry | Redirect fast path |
| `url:{destination}` | Short code | ≤ 5 minutes; never beyond expiry | Duplicate fast path |
| `geoip:{ip}` | Country | 24 hours | Avoid repeat provider calls |

## REST API

Base URL: `http://localhost:3000`

| Method | Path | Success | Purpose |
|---|---|---:|---|
| `POST` | `/api/links` | `201` | Create or reuse an active link |
| `GET` | `/api/links` | `200` | List the 25 newest links |
| `GET` | `/api/links/{code}` | `200` | Read one link |
| `PUT` | `/api/links/{code}` | `200` | Replace destination and optional expiry |
| `DELETE` | `/api/links/{code}` | `204` | Delete link and click history |
| `GET` | `/api/links/{code}/analytics` | `200` | Aggregates and recent clicks |
| `GET` | `/r/{code}` | `302` | Resolve a public short link |

### Create a custom, expiring link

```bash
curl -X POST http://localhost:3000/api/links \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com/guides/spring",
    "customAlias": "spring",
    "expiresAt": "2027-01-01T00:00:00Z"
  }'
```

```json
{
  "code": "spring",
  "shortUrl": "http://localhost:3000/r/spring",
  "destinationUrl": "https://example.com/guides/spring",
  "createdAt": "2026-10-04T08:30:00Z",
  "expiresAt": "2027-01-01T00:00:00Z",
  "active": true,
  "clicks": 0
}
```

### Update and inspect

```bash
curl -X PUT http://localhost:3000/api/links/spring \
  -H "Content-Type: application/json" \
  -d '{"url":"https://spring.io/projects/spring-boot","expiresAt":null}'

curl http://localhost:3000/api/links/spring/analytics
```

### Consistent error contract

```json
{
  "timestamp": "2026-10-04T08:32:15Z",
  "status": 409,
  "code": "ALIAS_TAKEN",
  "message": "That custom alias is already in use.",
  "details": {}
}
```

| Status | Code | Meaning |
|---:|---|---|
| `400` | `VALIDATION_ERROR` / `INVALID_REQUEST` | Malformed or unsafe input |
| `404` | `NOT_FOUND` | Code is missing or inactive |
| `409` | `ALIAS_TAKEN` | Custom alias is allocated |
| `410` | `LINK_EXPIRED` | Link passed its expiry |
| `429` | `RATE_LIMITED` | API allowance is exhausted |
| `500` | `INTERNAL_ERROR` | Safe response; details remain in logs |

## Swagger UI: complete API walkthrough

Swagger UI is the fastest way to explore the API without installing a separate REST client. Start
the stack, then open **[http://localhost:3000/swagger-ui/index.html](http://localhost:3000/swagger-ui/index.html)**.
The raw OpenAPI 3.1 document is available at
**[http://localhost:3000/v3/api-docs](http://localhost:3000/v3/api-docs)**.

The **Servers** selector at the top of Swagger UI must show `http://localhost:3000`. This is the
public nginx edge through which the browser can reach the application. `backend:8080` is only a
Docker-network address and must not appear in generated browser requests.

### How to execute an operation

Every operation follows the same interaction pattern:

1. Expand the operation, such as `POST /api/links`.
2. Select **Try it out**.
3. Fill in path parameters and, where required, replace the example JSON request body.
4. Select **Execute**.
5. Inspect the generated curl command, **Request URL**, response status, headers, and body.
6. Confirm that the Request URL begins with `http://localhost:3000`.

The workflow below uses `swguide` as a stable alias so the output from one operation becomes the
input to the next. If that alias already exists, delete it first or choose another 1-8 character
alphanumeric value.

```mermaid
flowchart LR
    Create[1. POST create] --> List[2. GET list]
    List --> Read[3. GET one]
    Read --> Redirect[4. GET redirect]
    Redirect --> Analytics[5. GET analytics]
    Analytics --> Update[6. PUT update]
    Update --> RedirectAgain[7. GET redirect again]
    RedirectAgain --> Delete[8. DELETE]
    Delete --> Confirm[9. GET returns 404]
```

### Step 1 - Create a short link

Expand **POST `/api/links`**, select **Try it out**, and use:

```json
{
  "url": "https://example.com/docs/getting-started",
  "customAlias": "swguide",
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

Select **Execute**. A new mapping returns `201 Created` and a body shaped like this:

```json
{
  "code": "swguide",
  "shortUrl": "http://localhost:3000/r/swguide",
  "destinationUrl": "https://example.com/docs/getting-started",
  "createdAt": "2026-10-04T08:30:00Z",
  "expiresAt": "2027-01-01T00:00:00Z",
  "active": true,
  "clicks": 0
}
```

Save the returned `code`; every code-based operation uses it. The server normalizes and validates
the destination, rejects schemes other than HTTP/HTTPS, verifies an optional alias, checks Redis,
confirms the result against PostgreSQL, and then stores both cache directions.

| Request field | Required | Rules |
|---|---:|---|
| `url` | Yes | Absolute `http://` or `https://` URI; unsafe characters are rejected |
| `customAlias` | No | 1-8 ASCII letters or digits; omit it or send `null` for an allocated code |
| `expiresAt` | No | Future ISO-8601 instant such as `2027-01-01T00:00:00Z`; omit or use `null` for no expiry |

To see duplicate detection, execute the same destination again with `customAlias` omitted:

```json
{
  "url": "https://example.com/docs/getting-started",
  "customAlias": null,
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

The response still uses `swguide`: an active destination is reused instead of producing multiple
codes. Duplicate lookup uses Redis as a fast hint and PostgreSQL as the authority. Note that the
API currently returns `201` for both a newly created mapping and an idempotently reused mapping.

### Step 2 - List recent links

Expand **GET `/api/links`**, select **Try it out**, and execute it.

- Path parameters: none
- Request body: none
- Expected status: `200 OK`
- Response: a JSON array containing up to the 25 newest links

```json
[
  {
    "code": "swguide",
    "shortUrl": "http://localhost:3000/r/swguide",
    "destinationUrl": "https://example.com/docs/getting-started",
    "createdAt": "2026-10-04T08:30:00Z",
    "expiresAt": "2027-01-01T00:00:00Z",
    "active": true,
    "clicks": 0
  }
]
```

This step confirms that the created mapping is persisted rather than existing only in Redis.

### Step 3 - Read one link

Expand **GET `/api/links/{code}`**, select **Try it out**, enter `swguide` for `code`, and execute.

- Path parameter: `code = swguide`
- Request body: none
- Expected status: `200 OK`
- Response: one `LinkResponse`, using the same shape returned by create

Use this operation to retrieve the current destination, expiry, activity state, and click count
before updating or deleting a mapping. An unknown or inactive code returns `404 Not Found`.

### Step 4 - Follow the short URL

Expand **GET `/r/{code}`**, select **Try it out**, enter `swguide`, and execute.

- Path parameter: `code = swguide`
- Request body: none
- Contract status: `302 Found`
- Important response header: `Location: https://example.com/docs/getting-started`

Swagger UI uses the browser Fetch API, which may automatically follow the redirect and show the
destination response rather than the intermediate `302`. That is browser behavior, not an API
failure. To inspect the exact redirect without following it, copy the generated curl and run:

```bash
curl --max-redirs 0 -i http://localhost:3000/r/swguide
```

The redirect path checks Redis first and falls back to PostgreSQL. Click collection starts only
after the destination is resolved and runs asynchronously, so the visitor is not held up by GeoIP
or analytics persistence.

### Step 5 - Inspect click analytics

Wait a moment for the asynchronous click writer, then expand
**GET `/api/links/{code}/analytics`**, enter `swguide`, and execute.

- Path parameter: `code = swguide`
- Request body: none
- Expected status: `200 OK`

```json
{
  "link": {
    "code": "swguide",
    "shortUrl": "http://localhost:3000/r/swguide",
    "destinationUrl": "https://example.com/docs/getting-started",
    "createdAt": "2026-10-04T08:30:00Z",
    "expiresAt": "2027-01-01T00:00:00Z",
    "active": true,
    "clicks": 1
  },
  "totalClicks": 1,
  "uniqueVisitors": 1,
  "countries": {
    "Local": 1
  },
  "recentClicks": [
    {
      "country": "Local",
      "device": "Desktop",
      "referrer": "Direct",
      "clickedAt": "2026-10-04T08:32:00Z"
    }
  ]
}
```

Local requests commonly appear as country `Local`; public client addresses can be enriched by the
configured GeoIP providers. `uniqueVisitors` is based on privacy-preserving visitor hashes rather
than storing raw visitor IP addresses in the click table.

### Step 6 - Update the destination or expiry

Expand **PUT `/api/links/{code}`**, enter `swguide`, and use this complete replacement payload:

```json
{
  "url": "https://example.org/updated-destination",
  "expiresAt": null
}
```

Select **Execute**. The operation returns `200 OK` with the updated `LinkResponse`. `null` removes
the expiry. To keep an expiry, supply a future ISO-8601 instant instead. The code itself cannot be
renamed; create a new alias if a different code is required.

Updating evicts the old `code -> URL` and `URL -> code` cache entries, commits the new mapping in
PostgreSQL, then warms the new cache entries. Existing analytics remain attached to the link.

### Step 7 - Confirm the update through the redirect

Execute **GET `/r/{code}`** again with `swguide`. The `Location` header now points to
`https://example.org/updated-destination`. This verifies the full write-through path rather than
only trusting the update response.

### Step 8 - Delete the link and its history

Expand **DELETE `/api/links/{code}`**, enter `swguide`, and execute.

- Path parameter: `code = swguide`
- Request body: none
- Expected status: `204 No Content`
- Response body: intentionally empty

Deletion evicts both cache directions and permanently removes the link. Its click-event rows are
removed by the database cascade, so the analytics history is deleted as well.

### Step 9 - Verify deletion

Execute **GET `/api/links/{code}`** and **GET `/api/links/{code}/analytics`** with `swguide`.
Both should now return `404 Not Found`. The redirect endpoint also returns `404` for this code.

### Endpoint-by-endpoint Swagger reference

| Operation | Swagger inputs | Request body | Success | Output / next step |
|---|---|---|---:|---|
| `POST /api/links` | None | `url`, optional `customAlias`, optional `expiresAt` | `201` | Save `code`; use it below |
| `GET /api/links` | None | None | `200` | Choose a `code` from the array |
| `GET /api/links/{code}` | `code` | None | `200` | Inspect current link state |
| `PUT /api/links/{code}` | `code` | `url`, optional `expiresAt` | `200` | Re-run read or redirect |
| `DELETE /api/links/{code}` | `code` | None | `204` | Re-run read to confirm `404` |
| `GET /api/links/{code}/analytics` | `code` | None | `200` | Inspect totals, countries, recent clicks |
| `GET /r/{code}` | `code` | None | `302` | Generates the click consumed by analytics |

### Exercising error responses in Swagger

Swagger also documents the non-success responses. These small scenarios make them easy to inspect:

**Invalid URL - `400 Bad Request`**

```json
{
  "url": "javascript:alert(1)",
  "customAlias": "bad",
  "expiresAt": null
}
```

**Alias conflict - `409 Conflict`**

Create alias `taken1` for one destination, then try to create the same alias for a different URL:

```json
{
  "url": "https://example.org/a-different-page",
  "customAlias": "taken1",
  "expiresAt": null
}
```

A repeated destination is duplicate reuse, whereas a repeated alias attached to a different
destination is a conflict.

**Expired redirect - `410 Gone`**

Create a link with a near-future expiry, wait until it passes, then execute `GET /r/{code}`. The
resolver deactivates and evicts it before returning the structured `410` error.

**Rate limit - `429 Too Many Requests`**

Execute API operations more than 20 times in one minute from the same client address. The limiter
is per application node and resets as tokens become available.

### Swagger troubleshooting

If Swagger generates curl commands containing `http://backend:8080`, an older backend or nginx
image is still running. Rebuild the two affected services and then hard-refresh the browser:

```bash
docker compose up --build -d backend web
```

Use `Ctrl+F5`, or open Swagger UI in a private window, to bypass cached JavaScript. Then open the
raw spec and confirm that its first server is public:

```bash
curl http://localhost:3000/v3/api-docs
```

The document should contain `"servers":[{"url":"http://localhost:3000"...}]`. Also confirm:

- `http://localhost:3000/actuator/health` reports `{"status":"UP"}`.
- All commands generated by Swagger begin with `http://localhost:3000`.
- Port `3000` is not already owned by another process.
- The `web` and `backend` services are healthy in `docker compose ps`.

## Configuration

| Environment variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/snip` | PostgreSQL address |
| `SPRING_DATASOURCE_USERNAME` | `snip` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `snip` | Local database password |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis hostname |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `APP_PUBLIC_BASE_URL` | `http://localhost:3000` | Returned short-link base |
| `APP_RATE_LIMIT_PER_MINUTE` | `20` | API allowance per source IP |
| `APP_CLEANUP_CRON` | `0 0 * * * *` | Expired-link cleanup schedule |
| `GRAFANA_PASSWORD` | `snip-admin` | Local Grafana password |

## Resilience behavior

| Failure | Expected behavior | Trade-off |
|---|---|---|
| Redis unavailable | Three short attempts, then database fallback | Higher database traffic |
| Primary GeoIP unavailable | Bounded retry, then fallback provider | Delayed enrichment |
| Both GeoIP providers unavailable | Store `Unknown` | Analytics works without geography |
| Analytics task fails | Log and preserve redirect | In-flight click can be lost |
| Alias allocation race | Database constraint selects one winner | Loser gets `409` |
| Database unavailable | Health fails; uncached operations fail | Redis is not durable failover |

## Testing and quality gates

Testing is layered so failures identify whether the defect is in business behavior, the HTTP
contract, the UI, image construction, or the running multi-service system.

### Test inventory

The repository currently contains **20 automated tests**.

| Suite | Tests | What it proves |
|---|---:|---|
| `LinkServiceTest` | 8 | URL normalization, duplicate reuse, custom-alias conflict, Redis redirect fast path, expiration/deactivation, cache eviction, update, and delete |
| `LinkControllerTest` | 6 | Create status and response, validation details, `302 Location`, forwarded client metadata, structured `404`, delete, and list contracts |
| `GeoIpServiceTest` | 1 | Loopback/private addresses are classified locally and never sent to a provider |
| `OpenApiConfigTest` | 1 | Swagger publishes the configured public edge and never leaks a trailing slash |
| `App.test.jsx` | 4 | Empty workspace, successful create flow, in-place statistics refresh, refreshed list, and visible server errors |

Backend tests use JUnit 5, Mockito, Hibernate Validator, and standalone MockMvc. Frontend tests use
Vitest, jsdom, Testing Library, and `user-event`. The service suite injects a fixed `Clock` where
time affects expiry, keeping assertions deterministic.

### Recommended: verify through Docker

This is the most reproducible path because it uses the same Java, Maven, Node, and nginx versions
as the final images.

```bash
# Build images; both Dockerfiles run their respective tests
docker compose build --no-cache backend web

# Start the complete integration environment
docker compose up -d

# All required dependencies should become healthy/running
docker compose ps

# Inspect startup, migration, and runtime logs
docker compose logs backend database redis web
```

A test failure stops the relevant image build. A database migration or application-startup failure
prevents the backend health check from passing, which in turn prevents dependent services from
starting as ready.

### Run backend tests locally

Prerequisites: JDK 21+ and Maven 3.9+.

```bash
cd backend

# Compile and execute all tests
mvn clean test

# Execute tests and create the JaCoCo HTML report
mvn clean verify
```

Reports are written to:

| Output | Location |
|---|---|
| Surefire results | `backend/target/surefire-reports/` |
| JaCoCo HTML | `backend/target/site/jacoco/index.html` |
| Packaged application | `backend/target/snip-api-1.0.0.jar` |

Run one class or one method while developing:

```bash
mvn -Dtest=LinkServiceTest test
mvn -Dtest=LinkServiceTest#returnsExistingActiveLinkForDuplicateDestination test
```

### Run frontend tests locally

Prerequisites: Node.js 22+ and npm. `npm ci` uses the checked-in lock file and fails if dependency
metadata drifts.

```bash
cd frontend
npm ci

# One deterministic test run
npm test

# Production bundle validation
npm run build
```

The production files are written to `frontend/dist/`. During a Docker build, nginx receives only
this compiled directory; source files and development dependencies do not enter the runtime image.

### End-to-end API smoke test

Start the stack, then create a deterministic alias:

```bash
curl -i -X POST http://localhost:3000/api/links \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/testing","customAlias":"smoke"}'
```

Expected: `201 Created`, code `smoke`, and `shortUrl` ending in `/r/smoke`.

Repeat the same request. Expected: the same active code is returned and the database still contains
one matching link. Then check the redirect without following it:

```bash
curl -i --max-redirs 0 http://localhost:3000/r/smoke
```

Expected: `302 Found` with `Location: https://example.com/testing`.

Because analytics is asynchronous, allow a brief moment before reading it:

```bash
curl http://localhost:3000/api/links/smoke/analytics
```

Expected: `totalClicks >= 1`, `uniqueVisitors >= 1`, and at least one recent-click record.

Update while retaining the code:

```bash
curl -i -X PUT http://localhost:3000/api/links/smoke \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.org/updated","expiresAt":null}'

curl -i --max-redirs 0 http://localhost:3000/r/smoke
```

Expected: the second command redirects to `https://example.org/updated`. Finally delete and verify
the cascade-facing API behavior:

```bash
curl -i -X DELETE http://localhost:3000/api/links/smoke
curl -i http://localhost:3000/api/links/smoke
curl -i http://localhost:3000/api/links/smoke/analytics
```

Expected: `204 No Content`, followed by two structured `404` responses.

### Validation and security scenarios

These commands exercise the boundaries that matter most:

```bash
# Unsupported scheme -> 400
curl -i -X POST http://localhost:3000/api/links \
  -H "Content-Type: application/json" \
  -d '{"url":"javascript:alert(1)"}'

# Embedded credentials -> 400
curl -i -X POST http://localhost:3000/api/links \
  -H "Content-Type: application/json" \
  -d '{"url":"https://user:password@example.com"}'

# Invalid alias -> 400 with details.customAlias
curl -i -X POST http://localhost:3000/api/links \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com","customAlias":"not-valid!"}'

# Missing link -> 404
curl -i http://localhost:3000/api/links/missing
```

To verify rate limiting, issue more than 20 API requests from the same source within a minute.
At least the exhausted requests should return `429`, `Retry-After`, `X-RateLimit-Limit`, and
`X-RateLimit-Remaining`. Public `/r/*` redirects are intentionally not counted by this API filter.

### Expiration test

Create a link with a future UTC timestamp, resolve it successfully, wait until it passes, and resolve
again. The post-expiry request should return `410 Gone`, mark the link inactive, and evict its cache
entries. Because request validation rejects already-past timestamps, use a comfortably future value
for manual testing.

```bash
curl -i -X POST http://localhost:3000/api/links \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/temporary","customAlias":"temp","expiresAt":"2027-01-01T00:00:00Z"}'
```

### Degraded-dependency checks

Perform these only in a disposable local environment.

**Redis fallback**

```bash
docker compose stop redis
curl -i -X POST http://localhost:3000/api/links \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/without-cache"}'
docker compose start redis
```

The request should still succeed through PostgreSQL after bounded Redis attempts, with warnings in
the backend log. Redirects for uncached records should behave the same way.

**GeoIP fallback**

Provider outages are best tested at the unit/service boundary with a controlled HTTP stub. In the
running application, both-provider failure should save the click with country `Unknown`; it must not
change the redirect status.

**Database loss**

Stopping PostgreSQL should make aggregate health fail and uncached create/read operations fail.
This confirms that Redis is not incorrectly presented as durable database failover.

### Health, Swagger, and metrics verification

```bash
curl -i http://localhost:3000/actuator/health
curl -i http://localhost:3000/v3/api-docs
curl -i http://localhost:3000/swagger-ui/index.html

# Prometheus is private, so query it from its container
docker compose exec -T prometheus \
  wget -qO- http://backend:8080/actuator/prometheus
```

Expected: public endpoints return `200`; Prometheus output contains JVM, HTTP server, process, and
HikariCP series. Open Grafana at `http://localhost:3001`, select **Snip API Overview**, exercise the
API, and confirm request-rate/latency panels receive data after the next 15-second scrape.

### Performance smoke and load profiles

With k6 installed, run the safe default profile:

```bash
k6 run performance/url-shortener.js
```

It uses three virtual users for ten seconds, creates one link in `setup()`, never follows the
external destination, and checks redirect correctness, failure rate, and p95 duration.

The larger local profile ramps to 100 virtual users:

```bash
k6 run -e PROFILE=load performance/url-shortener.js
```

The application limit is 20 API requests/minute per source IP. A single-machine load test can
therefore exercise `429` behavior. Raise `APP_RATE_LIMIT_PER_MINUTE` only for a dedicated load-test
environment, record the override with the result, and never present laptop results as production
capacity evidence.

### Continuous integration

`.github/workflows/ci.yml` runs for pull requests and pushes to `main`:

```mermaid
flowchart LR
    Commit[Push / pull request] --> Java[Java 21<br/>mvn clean verify]
    Commit --> Node[Node 22<br/>npm ci + test + build]
    Java --> Compose[Validate Compose]
    Node --> Compose
    Compose --> Images[Build backend + web images]
```

The workflow uses read-only repository permissions, caches Maven/npm downloads, validates the fully
resolved Compose model, and builds the two application images. It intentionally does not deploy.

### Test boundaries and next additions

The present suite is deterministic and fast, but it does not claim browser-matrix, chaos, or
production-capacity coverage. PostgreSQL and Redis are exercised by the Compose smoke workflow;
unit tests mock infrastructure so an image build does not require a nested Docker daemon.

High-value next additions are PostgreSQL/Redis Testcontainers repository tests, an nginx-to-backend
contract test, accessibility automation, a real-browser happy path, concurrency tests for alias
races, and failure-injection tests around Redis and GeoIP timeouts.

### Troubleshooting tests

| Symptom | Check |
|---|---|
| Backend image stops during `mvn package` | Read the failing Surefire test name earlier in the build output |
| Frontend image stops during `npm test` | Run `npm ci && npm test` inside `frontend/` for complete Vitest output |
| Backend remains unhealthy | Run `docker compose logs backend database`; look for Flyway or connection errors |
| UI loads but API calls fail | Verify `backend` is healthy and inspect `docker compose logs web backend` |
| Analytics count updates slowly | Click persistence is asynchronous; inspect backend warnings and wait briefly |
| Grafana panels are empty | Generate API traffic, wait one scrape interval, and check the Prometheus target |
| Alias tests unexpectedly conflict | Remove the test alias or reset disposable volumes with `docker compose down -v` |

## Project structure

```text
.
├── backend/
│   ├── src/main/java/dev/snip/
│   │   ├── common/           Error contract and exception mapping
│   │   ├── config/           OpenAPI and rate limiting
│   │   └── link/             Domain, services, repositories, REST
│   ├── src/main/resources/   Configuration and Flyway migration
│   └── src/test/             JUnit, Mockito, MockMvc
├── frontend/                 React UI, nginx, Vitest tests
├── monitoring/               Prometheus and provisioned Grafana
├── performance/              k6 smoke/load profiles
├── docs/screenshots/         Verified application screenshots
├── .github/workflows/ci.yml  CI quality gates
└── compose.yaml              Complete local topology
```

## Engineering decisions and limits

**Why PostgreSQL and Redis?** PostgreSQL supplies transactions, constraints, and durable history.
Redis removes frequent reads without becoming another authority. Cache loss affects latency, not
correctness.

**Why `@Async` instead of Kafka?** Kafka was excluded from the requested scope. In-process async
work keeps normal analytics off the redirect thread with less infrastructure. The honest cost is
durability: a process crash can lose an in-flight event.

Before internet deployment:

- add authentication, ownership, and tenant isolation to management endpoints;
- move JVM-local rate limits to shared storage or an edge gateway for multiple replicas;
- review GeoIP provider licensing/privacy or use a local database;
- add a durable queue when analytics replay and delivery guarantees are required;
- add PostgreSQL/Redis Testcontainers for deeper infrastructure integration coverage;
- add TLS, secret management, trusted-proxy configuration, and cloud infrastructure.
