# Security controls and verification

Updated 18 October 2026. This is an implementation report, not an independent pentest certificate or score.

## Request routing and capacity

Live Vercel project inspection confirmed `resourceConfig.fluid=true`, Node 24 and a default function region of `iad1`. Vercel manages traffic routing, instance reuse and scaling. No additional proxy or single-instance in-memory quota is introduced. Neon requests use the existing serverless HTTP client; business data and limits are shared across function instances.

No capacity or concurrent-user guarantee is claimed. Application rate checks themselves use database capacity. The edge rule reduces requests reaching functions, but infrastructure quotas, database latency and provider limits still constrain capacity. No regional failover has been purchased or configured.

## Shared request limits

The `boundary_take_rate_limit` PostgreSQL function increments counters atomically. Counters contain a keyed hash of the subject, never plaintext phone numbers or bearer tokens. Expired counters are removed in bounded batches. Requests fail closed if the limiter database is unavailable. Limits use fixed windows; a caller can use the end of one window and the beginning of another, so these are not smooth token buckets.

| Scope | Limit |
| --- | --- |
| Vercel edge `/api/` | 600 requests / IP / minute |
| Each API's reads | 240 / IP / minute |
| Each API's writes | 60 / IP / minute |
| Device linking | 10 / IP / ten minutes |
| Player lookup and squad validation | 20 / linked account / minute |
| Player account writes | 15 / account / minute |
| Shop account reads | 60 / account / minute |
| Shop account writes and payment verification | 15 / account / minute |
| Community writes | 20 / account / minute, plus existing post/reply quotas |
| Match publication | 60 / write authority / minute, plus IP quota |

`429` replies include `Retry-After`; application checks expose remaining quota headers. Outside Vercel, forwarding headers are ignored in favour of the connection address. The production Vercel ingress supplies the client address. A proxy added in front of Vercel needs an explicit trust review. Shared campus/NAT networks may require adjusted IP quotas after monitoring.

## Request and browser controls

- JSON content type and bounded request bytes before write processing: players 6 KB, community 8 KB, shop 12 KB, match publication 250 KB.
- Explicit origins for browser requests and rejection of cross-site writes. Native clients may omit Origin and use their existing bearer/device authority. Origin checks supplement authentication; they do not replace it.
- Strict UUID validation for match IDs and capped write-key lengths. Malformed JSON returns a client error. Public errors avoid database internals and secret-containing exception messages.
- Browser headers: HSTS, nosniff, frame denial, referrer policy, restricted camera/microphone/geolocation and a CSP provider allowlist.
- Legacy score/overlay pages still contain inline scripts and styles, so CSP currently permits inline content. This is a documented hardening limitation; the app does not claim a strict nonce-only CSP. Third-party sign-in/checkout scripts require their allowlisted domains.
- Shop design HTML uses only a local, allowlisted artwork category; catalog/user text uses text nodes.

## Existing authority and privacy boundaries

Phone registration and shop writes require server-verified Clerk accounts. Linked device sessions store hashes on the server and expire; phone lookup is private and quota-limited. Match publication remains capability based: the device-owned recovery key controls its match. The user explicitly requested no local PIN. An unlocked scoring device and the selected workspace are therefore a local trust boundary, not a distributed official-role permission system.

Payment confirmation checks account ownership, signature, server amount/currency and capture. No payment credentials are committed. Razorpay processes card/UPI credentials. Provider failures preserve durable order recovery. Refunding a late captured payment on a cancelled order uses an idempotency key. Browser success does not by itself prove payment capture or dispatch.

## Verification performed

`node --env-file=.env.local scripts/verify-security.mjs` checks a real shared limiter with twelve concurrent requests and quota five, independent-subject isolation, disallowed origin, wrong content type, oversized body, forged checkout signature and ignored forwarding headers outside Vercel. It cleans up only its dedicated verification quota records. No live charges/refunds or customer data changes are part of that check.

`npm audit --omit=dev` reported zero known vulnerabilities in the current npm dependency set. This does not audit Android dependencies, undisclosed vulnerabilities, provider settings or every business flow. Syntax checks and independent review accompany the change. Production HTTP checks confirm deployed headers, basic rejection paths and expected quota metadata; full load testing, authenticated account/payment penetration testing, SMS acceptance and an external OWASP assessment remain outstanding.

## Operator commands

```powershell
npx vercel@latest firewall status
npx vercel@latest firewall traffic list
node --env-file=.env.local scripts/verify-security.mjs
npm audit --omit=dev
```

Vercel DDoS mitigations were reported Active. Attack Mode is intentionally off because a browser challenge would interfere with native Android API requests. Managed OWASP rules and bot protection were reported off at inspection; this report does not claim they are enabled. Review measured traffic and plan capabilities before enabling challenge-based controls.

References: [Vercel Fluid Compute](https://vercel.com/docs/fluid-compute), [Vercel request headers](https://vercel.com/docs/headers/request-headers), [Vercel Firewall](https://vercel.com/docs/vercel-firewall).
