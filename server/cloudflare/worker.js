const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
  "Content-Type": "application/json; charset=utf-8"
};

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { headers: cors });
    const url = new URL(request.url);
    if (url.pathname !== "/chat" || request.method !== "POST") {
      return new Response(JSON.stringify({ error: "Not found" }), { status: 404, headers: cors });
    }
    if (!env.GEMINI_API_KEY) {
      return new Response(JSON.stringify({ error: "Server model key is not configured" }), { status: 503, headers: cors });
    }
    try {
      const body = await request.json();
      const message = String(body.message || "").trim().slice(0, 12000);
      const language = String(body.language || "ar-EG").slice(0, 24);
      if (!message) return new Response(JSON.stringify({ error: "Message is required" }), { status: 400, headers: cors });

      const upstream = await fetch(
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-goog-api-key": env.GEMINI_API_KEY
          },
          body: JSON.stringify({
            systemInstruction: {
              parts: [{ text: "You are MEMORI AI, a helpful multilingual assistant. Reply naturally in the user's language. If the user writes Egyptian Arabic, answer in Egyptian Arabic. Be accurate, concise, and never claim to have performed actions on the device unless the app confirms them." }]
            },
            contents: [{ role: "user", parts: [{ text: message }] }],
            generationConfig: { temperature: 0.6, maxOutputTokens: 1200 }
          })
        }
      );
      const data = await upstream.json();
      if (!upstream.ok) {
        return new Response(JSON.stringify({ error: "Model provider error", details: data.error?.message || "Request failed" }),
          { status: 502, headers: cors });
      }
      const reply = (data.candidates?.[0]?.content?.parts || [])
        .map(part => part.text || "").join("").trim();
      if (!reply) return new Response(JSON.stringify({ error: "Empty model response" }), { status: 502, headers: cors });
      return new Response(JSON.stringify({ reply }), { headers: cors });
    } catch (error) {
      return new Response(JSON.stringify({ error: "Invalid request or upstream failure" }), { status: 400, headers: cors });
    }
  }
};
