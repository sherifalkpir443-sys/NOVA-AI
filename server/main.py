import os
from typing import Optional

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

app = FastAPI(title="MEMORI AI Gateway", version="1.0.0")

allowed_origins = [x.strip() for x in os.getenv("ALLOWED_ORIGINS", "").split(",") if x.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins or [],
    allow_credentials=False,
    allow_methods=["POST", "GET"],
    allow_headers=["Content-Type", "Authorization"],
)

class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=12000)
    language: Optional[str] = Field(default="ar-EG", max_length=32)

class ChatResponse(BaseModel):
    reply: str

@app.get("/health")
async def health():
    return {"status": "ok", "service": "MEMORI AI Gateway"}

@app.post("/chat", response_model=ChatResponse)
async def chat(body: ChatRequest):
    api_key = os.getenv("MODEL_API_KEY", "").strip()
    base_url = os.getenv("MODEL_BASE_URL", "https://api.openai.com/v1").rstrip("/")
    model = os.getenv("MODEL_NAME", "").strip()
    if not api_key or not model:
        raise HTTPException(status_code=503, detail="AI provider is not configured on the server")

    system_prompt = (
        "You are MEMORI AI, a helpful multilingual assistant. "
        "Reply in the user's requested language. Be accurate and clearly state uncertainty."
    )
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": f"Language: {body.language or 'ar-EG'}\n\n{body.message}"},
        ],
        "temperature": 0.7,
    }
    timeout = httpx.Timeout(60.0, connect=15.0)
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.post(
                f"{base_url}/chat/completions",
                headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                json=payload,
            )
        response.raise_for_status()
        data = response.json()
        reply = data["choices"][0]["message"]["content"]
        if not isinstance(reply, str) or not reply.strip():
            raise ValueError("Empty model response")
        return ChatResponse(reply=reply.strip())
    except httpx.HTTPStatusError as exc:
        # Do not expose provider response bodies, headers, or credentials to the app.
        raise HTTPException(status_code=502, detail=f"AI provider returned HTTP {exc.response.status_code}") from exc
    except (httpx.HTTPError, KeyError, IndexError, ValueError) as exc:
        raise HTTPException(status_code=502, detail="AI provider request failed") from exc
