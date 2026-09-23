from fastapi import FastAPI
from fastapi.responses import FileResponse, JSONResponse
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

@app.post("/download")
def download(req: DownloadRequest):
    platform = detect_platform(req.url)
    file_id = str(uuid.uuid4())
    out_path = f"downloads/{file_id}.%(ext)s"

    if platform == "youtube" and req.quality != "best":
        fmt = f"bestvideo[height<={req.quality[:-1]}]+bestaudio/best[height<={req.quality[:-1]}]/best"
    else:
        fmt = "best"

    ydl_opts = {"outtmpl": out_path, "format": fmt, "quiet": True}
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(req.url, download=True)
            filename = ydl.prepare_filename(info)
        basename = os.path.basename(filename)
        return {"platform": platform, "filename": basename, "status": "done"}
    except Exception as e:
        return JSONResponse(status_code=200, content={"platform": platform, "status": "error", "error": str(e)})

@app.get("/file/{filename}")
def get_file(filename: str):
    path = f"downloads/{filename}"
    return FileResponse(path, media_type="video/mp4", filename=filename)
