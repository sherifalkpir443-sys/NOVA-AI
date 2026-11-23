# MEMORI AI: local + primary + backup

## Android routing
- Local deterministic calculator and basic offline responses run first.
- The primary HTTPS endpoint is tried next.
- If it fails or times out, the backup HTTPS endpoint is tried.
- If both fail or are blank, the app returns an honest offline fallback.
- No fake endpoint is prefilled.

## Primary
Deploy `server/cloudflare/worker.js` as a Cloudflare Worker and set its `GEMINI_API_KEY` secret. It is serverless hosting, not a dedicated always-on machine; quotas and terms can change.

## Independent backup
Deploy `server/backup-node/server.js` on a hosting provider independent of Cloudflare or on a server you control. It supports `GET /health` and `POST /chat`.
Environment variables: `MODEL_API_KEY` (secret), `MODEL_API_URL`, `MODEL_NAME`, and optional `PORT`. No paid provider is selected by default: choose a provider with a free tier if available, and check its current limits and terms before use. Free tiers can change and are not guaranteed to be unlimited. Never put keys in the APK or Git repository.

After both are deployed with HTTPS, enter their real base URLs in the app settings. A blank endpoint is safer than a placeholder.

## Limits / security
These are deployable templates, not live hosted servers. Deployment, DNS/HTTPS, provider credentials, rate limiting, authentication, monitoring and uptime still need setup. Do not expose either sample publicly without adding authentication and rate limiting. Different hosting platforms reduce one infrastructure failure mode, but a shared model provider or internet outage can still affect both.
