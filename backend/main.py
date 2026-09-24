from fastapi import FastAPI
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel
import yt_dlp
import uuid
import os
import re
import requests

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
    elif "pinterest.com" in url or "pin.it" in url:
        return "pinterest"
    return "unknown"

def download_pinterest_image(url, file_id):
    r = requests.get(url, headers={"User-Agent": "Mozilla/5.0"}, allow_redirects=True, timeout=20)
    match = re.search(r'<meta property="og:image" content="([^"]+)"', r.text)
    if not match:
        raise Exception("No image or video found on this Pinterest link")
    img_url = match.group(1)
    img_data = requests.get(img_url, timeout=20).content
    filename = f"{file_id}.jpg"
    with open(f"downloads/{filename}", "wb") as f:
        f.write(img_data)
    return filename

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
    if platform == "youtube":
        ydl_opts["extractor_args"] = {"youtube": {"player_client": ["android", "web"]}}
        ydl_opts["http_headers"] = {"User-Agent": "com.google.android.youtube/19.09.37"}

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(req.url, download=True)
            filename = ydl.prepare_filename(info)
        basename = os.path.basename(filename)
        return {"platform": platform, "filename": basename, "status": "done", "type": "video"}
    except Exception as e:
        if platform == "pinterest":
            try:
                basename = download_pinterest_image(req.url, file_id)
                return {"platform": platform, "filename": basename, "status": "done", "type": "image"}
            except Exception as e2:
                return JSONResponse(status_code=200, content={"platform": platform, "status": "error", "error": str(e2)})
        return JSONResponse(status_code=200, content={"platform": platform, "status": "error", "error": str(e)})

@app.get("/file/{filename}")
def get_file(filename: str):
    path = f"downloads/{filename}"
    media_type = "image/jpeg" if filename.endswith(".jpg") else "video/mp4"
    return FileResponse(path, media_type=media_type, filename=filename)
