# MEMORI AI Cloudflare Worker backend

This is a lightweight HTTPS proxy for the Android app's existing `POST /chat` contract.
It keeps the Gemini API key on the server; never place the key in the APK or source control.

## Deploy on the free Cloudflare Workers plan

1. Create a Cloudflare account and open Workers & Pages.
2. Create a Worker named `memori-ai-api`.
3. Upload `worker.js` (or deploy this folder with Wrangler).
4. In Worker Settings → Variables and Secrets, add a secret named `GEMINI_API_KEY`.
5. Create a Gemini API key in Google AI Studio and paste it into that secret.
6. Deploy. Your app endpoint is `https://YOUR-WORKER.YOUR-SUBDOMAIN.workers.dev`.
7. Enter that HTTPS address in MEMORI AI Settings.

The Worker plan has a free request allowance, but the model provider's free tier has its own rate limits and can change. This is not unlimited compute. Protect the public endpoint with rate limiting before sharing the app widely; anyone who discovers a public URL could otherwise consume the model quota.

The Android app expects `POST /chat` with JSON `{ "message": "...", "language": "ar-EG" }` and a JSON response `{ "reply": "..." }`.
