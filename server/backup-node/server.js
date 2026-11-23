import http from "node:http";

const port = Number(process.env.PORT || 8080);
const apiKey = process.env.MODEL_API_KEY;
const model = process.env.MODEL_NAME;
const apiUrl = process.env.MODEL_API_URL;
const allowedOrigin = process.env.ALLOWED_ORIGIN || "*";
const send = (res, status, body) => {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": allowedOrigin,
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization"
  });
  res.end(JSON.stringify(body));
};

const server = http.createServer(async (req, res) => {
  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "Access-Control-Allow-Origin": allowedOrigin,
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization"
    });
    return res.end();
  }
  if (req.method === "GET" && req.url === "/health") {
    return send(res, 200, {
      ok: true,
      service: "memori-backup",
      providerConfigured: Boolean(apiKey && model && apiUrl)
    });
  }
  if (req.method !== "POST" || req.url !== "/chat") {
    return send(res, 404, { error: "Not found" });
  }
  if (!apiKey || !model || !apiUrl) {
    return send(res, 503, { error: "Backup provider is not configured" });
  }
  try {
    let raw = "";
    for await (const chunk of req) {
      raw += chunk;
      if (Buffer.byteLength(raw) > 16000) {
        req.destroy();
        return send(res, 413, { error: "Request too large" });
      }
    }
    const input = JSON.parse(raw || "{}");
    const message = String(input.message || "").trim().slice(0, 12000);
    const language = String(input.language || "ar-EG").slice(0, 24);
    if (!message) return send(res, 400, { error: "Message required" });

    const upstream = await fetch(apiUrl, {
      method: "POST",
      headers: {
        "Authorization": "Bearer " + apiKey,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model,
        messages: [
          { role: "system", content: "You are MEMORI AI. Reply in " + language + "; use Egyptian Arabic for ar-EG. Never claim actions not confirmed by the app." },
          { role: "user", content: message }
        ],
        temperature: 0.6
      }),
      signal: AbortSignal.timeout(25000)
    });
    const data = await upstream.json();
    if (!upstream.ok) return send(res, 502, { error: "Backup model provider error", status: upstream.status });
    const reply = data.choices?.[0]?.message?.content?.trim();
    if (!reply) return send(res, 502, { error: "Empty model reply" });
    return send(res, 200, { reply });
  } catch (error) {
    return send(res, 502, { error: "Backup model request failed" });
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log("MEMORI backup listening on " + port);
});
