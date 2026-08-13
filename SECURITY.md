# Security Policy

Restitch performs outbound HTTP requests from application code, so security reports are especially important when they involve host selection, header forwarding, request limits, streaming cancellation, or error disclosure.

## Supported Versions

Before `1.0.0`, only the latest released minor line receives security fixes.

| Version | Supported |
|---------|-----------|
| Latest `0.x` | Yes |
| Older `0.x` | No |

## Reporting A Vulnerability

Please do not open a public issue with exploit details.

Use GitHub private vulnerability reporting if it is enabled for the repository. If it is not enabled, open a minimal public issue asking for a private reporting channel and omit sensitive details.

Include:

- Affected version or commit
- A small reproduction when possible
- Expected impact
- Whether credentials, headers, cookies, or downstream hosts are involved
- Any suggested mitigation

## Project Security Boundaries

Restitch should:

- Resolve outbound hosts only from named configured clients
- Forward only allowlisted inbound headers
- Avoid cross-request caching
- Bound per-object bytes, root buffering, session entries, session bytes, pending IDs, batch size, batch window, and concurrency
- Stop parsing and pending HTTP work when reactive streams are cancelled
- Avoid exposing raw downstream bodies, credentials, cookies, or unrestricted URLs in errors
