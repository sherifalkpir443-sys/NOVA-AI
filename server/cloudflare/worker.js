const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
  "Content-Type": "application/json; charset=utf-8"
};
function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: cors });
}
export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { headers: cors });
    const url = new URL(request.url);
    if (url.pathname === "/health" && request.method === "GET") {
      return json({ ok: true, service: "memori-ai-api", modelConfigured: Boolean(env.GEMINI_API_KEY) }, env.GEMINI_API_KEY ? 200 : 503);
    }
    if (url.pathname !== "/chat" || request.method !== "POST") return json({ error: "Not found" }, 404);
    if (!env.GEMINI_API_KEY) return json({ error: "Server model key is not configured" }, 503);
    try {
      const body = await request.json();
      const message = String(body.message || "").trim().slice(0, 12000);
      if (!message) return json({ error: "Message is required" }, 400);
      const upstream = await fetch("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent", {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: "You are MEMORI AI, a helpful multilingual assistant. Reply naturally in the user's language. If the user writes Egyptian Arabic, answer in Egyptian Arabic. Be accurate, concise, and never claim to have performed actions on the device unless the app confirms them." }] },
          contents: [{ role: "user", parts: [{ text: message }] }],
          generationConfig: { temperature: 0.6, maxOutputTokens: 1200 }
        }),
        signal: AbortSignal.timeout(15000)
      });
      const data = await upstream.json();
      if (!upstream.ok) return json({ error: "Model provider error", details: data.error?.message || "Request failed" }, 502);
      const reply = (data.candidates?.[0]?.content?.parts || []).map(part => part.text || "").join("").trim();
      if (!reply) return json({ error: "Empty model response" }, 502);
      return json({ reply });
    } catch {
      return json({ error: "Invalid request or upstream failure" }, 502);
    }
  }
};
