# MEMORI AI — backup server

This is a real Node.js backup service for the Android app's existing API contract:
- `GET /health`
- `POST /chat` with `{"message":"..."}`
- returns `{"reply":"..."}`

## Providers and failover

The server tries OpenAI first when `OPENAI_API_KEY` is configured, then Gemini if OpenAI fails. If only Gemini is configured, it uses Gemini. API keys are environment secrets and must never be committed or placed in the APK.

Environment variables:
- `PORT` (optional; default 8787)
- `OPENAI_API_KEY` (optional)
- `OPENAI_MODEL` (optional; default `gpt-4.1-mini`)
- `GEMINI_API_KEY` (optional fallback)
- `ALLOWED_ORIGIN` (optional CORS origin; set to your app's trusted web origin if applicable)

## Run locally

Requires Node.js 20+:

```sh
npm start
```

Then test `http://localhost:8787/health`. Configure at least one provider key in the hosting platform's secret/environment settings.

## Deploy

Deploy this directory to a Node.js host that supports persistent web services (for example, a free-tier host if currently available). Free hosting can sleep, impose quotas, or change terms; it is not guaranteed always-on. Keep Cloudflare Worker as the first endpoint and configure this server as the app's secondary endpoint only after deployment and a successful `/health` test.

## Security and cost

- Keep keys only in host-side environment secrets.
- Add host-level rate limiting/authentication before public distribution. This starter has request-size limits but is not a complete abuse-protection layer.
- Model API calls may cost money and have provider quotas. A free Android app does not make cloud inference free or unlimited.
