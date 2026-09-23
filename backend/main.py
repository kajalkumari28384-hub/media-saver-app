from fastapi import FastAPI
from pydantic import BaseModel
import yt_dlp
import uuid
import os

app = FastAPI()
os.makedirs("downloads", exist_ok=True)

class LinkRequest(BaseModel):
    url: str

class DownloadRequest(BaseModel):
    url: str
    quality: str = "best"

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

@app.post("/formats")
def get_formats(req: LinkRequest):
    ydl_opts = {"quiet": True}
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(req.url, download=False)
        formats = []
        for f in info.get("formats", []):
            if f.get("height"):
                formats.append(f"{f['height']}p")
        return {"platform": detect_platform(req.url), "qualities": sorted(set(formats), reverse=True)}

@app.post("/download")
def download(req: DownloadRequest):
    file_id = str(uuid.uuid4())
    out_path = f"downloads/{file_id}.%(ext)s"
    fmt = "best" if req.quality == "best" else f"bestvideo[height<={req.quality[:-1]}]+bestaudio/best"
    ydl_opts = {"outtmpl": out_path, "format": fmt, "quiet": True}
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(req.url, download=True)
        filename = ydl.prepare_filename(info)
    return {"platform": detect_platform(req.url), "file": filename, "status": "done"}
