from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()

class LinkRequest(BaseModel):
    url: str

def detect_platform(url: str) -> str:
    url = url.lower()
    if "youtube.com" in url or "youtu.be" in url:
        return "youtube"
    elif "instagram.com" in url:
        return "instagram"
    elif "twitter.com" in url or "x.com" in url:
        return "twitter"
    elif "pinterest.com" in url:
        return "pinterest"
    return "unknown"

@app.get("/")
def health():
    return {"status": "running"}

@app.post("/detect")
def detect(req: LinkRequest):
    return {"platform": detect_platform(req.url)}
