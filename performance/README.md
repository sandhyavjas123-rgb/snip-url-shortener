# Performance checks

The default profile is a safe 10-second smoke test. It creates one link, resolves it without
following the external redirect, and checks the redirect status and latency threshold.

```bash
k6 run performance/url-shortener.js
```

An intentionally modest local load profile is also available:

```bash
k6 run -e PROFILE=load performance/url-shortener.js
```

The API rate limit is 20 requests/minute per client IP, so load runs through one source address
will exercise `429` behavior. Raise `APP_RATE_LIMIT_PER_MINUTE` only in a dedicated performance
environment; do not treat a laptop result as a production capacity claim.
