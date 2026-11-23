import http from "node:http";
const port = Number(process.env.PORT || 8080);
const apiKey = process.env.MODEL_API_KEY;
const model = process.env.MODEL_NAME || "gpt-4o-mini";
const apiUrl = process.env.MODEL_API_URL || "https://api.openai.com/v1/chat/completions";
const server = http.createServer(async (req, res) => {
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  if (req.method === "GET" && req.url === "/health") {
    res.writeHead(200); return res.end(JSON.stringify({ok:true,service:"memori-backup"}));
  }
  if (req.method !== "POST" || req.url !== "/chat") {
    res.writeHead(404); return res.end(JSON.stringify({error:"Not found"}));
  }
  if (!apiKey) { res.writeHead(503); return res.end(JSON.stringify({error:"MODEL_API_KEY is not configured"})); }
  try {
    let raw = "";
    for await (const chunk of req) { raw += chunk; if (raw.length > 20000) throw new Error("Request too large"); }
    const input = JSON.parse(raw);
    const message = String(input.message || "").trim().slice(0,12000);
    const language = String(input.language || "ar-EG").slice(0,24);
    if (!message) { res.writeHead(400); return res.end(JSON.stringify({error:"Message required"})); }
    const upstream = await fetch(apiUrl, {
      method:"POST",
      headers:{"Authorization":`Bearer ${apiKey}`,"Content-Type":"application/json"},
      body:JSON.stringify({model,messages:[
        {role:"system",content:`You are MEMORI AI. Reply in ${language}; use Egyptian Arabic for ar-EG. Never claim actions not confirmed by the app.`},
        {role:"user",content:message}],temperature:0.6}),
      signal:AbortSignal.timeout(25000)
    });
    const data = await upstream.json();
    if (!upstream.ok) throw new Error("Provider error");
    const reply = data.choices?.[0]?.message?.content?.trim();
    if (!reply) throw new Error("Empty reply");
    res.writeHead(200); res.end(JSON.stringify({reply}));
  } catch (_) { res.writeHead(502); res.end(JSON.stringify({error:"Backup model request failed"})); }
});
server.listen(port,"0.0.0.0",()=>console.log(`MEMORI backup listening on ${port}`));
