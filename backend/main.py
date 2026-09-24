from fastapi import FastAPI
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel
import yt_dlp
import uuid
import os
import re
import json
import html
import requests
from urllib.parse import urlparse

app = FastAPI()
os.makedirs("downloads", exist_ok=True)

class LinkRequest(BaseModel):
    url: str

class DownloadRequest(BaseModel):
    url: str
    quality: str = "best"


def detect_platform(url: str) -> str:
    u = url.lower()
    if "youtube.com" in u or "youtu.be" in u:
        return "youtube"
    if "instagram.com" in u:
        return "instagram"
    if "twitter.com" in u or "x.com" in u:
        return "twitter"
    if "pinterest.com" in u or "pin.it" in u:
        return "pinterest"
    return "unknown"


PIN_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Linux; Android 15) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/140.0 Mobile Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/json",
    "Accept-Language": "en-US,en;q=0.9",
    "Referer": "https://www.pinterest.com/",
}


def resolve_pinterest(url):
    r = requests.get(
        url,
        headers=PIN_HEADERS,
        allow_redirects=True,
        timeout=30
    )
    r.raise_for_status()
    return r.url, r.text


def get_pin_id(url, page):
    patterns = [
        r"/pin/(?:[^/?#]+--)?(\d+)",
        r'"pinId"\s*:\s*"?(\d+)',
        r'"pin_id"\s*:\s*"?(\d+)',
    ]

    for pattern in patterns:
        m = re.search(pattern, url)
        if m:
            return m.group(1)

        m = re.search(pattern, page)
        if m:
            return m.group(1)

    return None


def pinterest_api(pin_id):
    url = "https://www.pinterest.com/resource/PinResource/get/"

    params = {
        "data": json.dumps({
            "options": {
                "field_set_key": "unauth_react_main_pin",
                "id": pin_id
            }
        })
    }

    headers = dict(PIN_HEADERS)
    headers["X-Pinterest-PWS-Handler"] = "www/[username].js"

    r = requests.get(
        url,
        params=params,
        headers=headers,
        timeout=30
    )

    r.raise_for_status()

    return r.json().get("resource_response", {}).get("data")


def find_media(data):
    videos = []
    images = []

    def walk(obj):
        if isinstance(obj, dict):

            video_list = obj.get("video_list")

            if isinstance(video_list, dict):
                for item in video_list.values():
                    if isinstance(item, dict) and item.get("url"):
                        videos.append({
                            "url": item["url"],
                            "width": item.get("width", 0) or 0,
                            "height": item.get("height", 0) or 0
                        })

            imgs = obj.get("images")

            if isinstance(imgs, dict):
                for item in imgs.values():
                    if isinstance(item, dict) and item.get("url"):
                        images.append({
                            "url": item["url"],
                            "width": item.get("width", 0) or 0,
                            "height": item.get("height", 0) or 0
                        })

            for value in obj.values():
                walk(value)

        elif isinstance(obj, list):
            for item in obj:
                walk(item)

    walk(data)

    return videos, images


def html_media(page):
    images = []
    videos = []

    tags = re.findall(r"<meta\b[^>]*>", page, re.I)

    for tag in tags:
        attrs = dict(
            re.findall(
                r"""([\w:-]+)\s*=\s*["']([^"']*)["']""",
                tag,
                re.I
            )
        )

        key = (
            attrs.get("property")
            or attrs.get("name")
            or ""
        ).lower()

        value = attrs.get("content")

        if not value:
            continue

        value = html.unescape(value)

        if key in {
            "og:image",
            "og:image:url",
            "twitter:image",
            "twitter:image:src"
        }:
            images.append(value)

        if key in {
            "og:video",
            "og:video:url",
            "og:video:secure_url",
            "twitter:player:stream"
        }:
            videos.append(value)

    page = html.unescape(page).replace("\\/", "/")

    videos += re.findall(
        r'https?://[^"\']+?\.(?:mp4|m4v)(?:\?[^"\']*)?',
        page,
        re.I
    )

    images += re.findall(
        r'https?://[^"\']+?i\.pinimg\.com[^"\']+?\.(?:jpg|jpeg|png|webp)(?:\?[^"\']*)?',
        page,
        re.I
    )

    return list(dict.fromkeys(images)), list(dict.fromkeys(videos))


def download_direct(url, file_id, referer):
    headers = {
        "User-Agent": PIN_HEADERS["User-Agent"],
        "Accept": "*/*",
        "Referer": referer,
    }

    # Pinterest often serves videos as HLS/M3U8.
    # Never save an M3U8 playlist as an MP4.
    is_hls = (
        ".m3u8" in url.lower()
        or "m3u8" in url.lower()
    )

    if is_hls:
        output = f"downloads/{file_id}.%(ext)s"

        opts = {
            "outtmpl": output,
            "format": "best",
            "merge_output_format": "mp4",
            "quiet": True,
            "http_headers": headers,
        }

        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)
            filename = os.path.basename(
                ydl.prepare_filename(info)
            )

        # yt-dlp may produce .mp4 after merging.
        candidates = [
            f"downloads/{file_id}.mp4",
            f"downloads/{filename}",
        ]

        for path in candidates:
            if os.path.exists(path):
                with open(path, "rb") as f:
                    head = f.read(32)

                if b"ftyp" in head:
                    return os.path.basename(path), "video"

        raise Exception("Pinterest HLS video was not converted to a valid MP4")

    r = requests.get(
        url,
        headers=headers,
        timeout=90
    )
    r.raise_for_status()

    data = r.content

    if not data:
        raise Exception("Pinterest returned an empty video")

    content_type = (
        r.headers.get("content-type", "")
        .lower()
        .split(";")[0]
    )

    # A real MP4 normally contains an ftyp box near the beginning.
    if (
        "video/" in content_type
        or data[4:8] == b"ftyp"
        or data[:16].find(b"ftyp") >= 0
    ):
        filename = f"{file_id}.mp4"
        filepath = os.path.join("downloads", filename)

        with open(filepath, "wb") as f:
            f.write(data)

        with open(filepath, "rb") as f:
            if b"ftyp" not in f.read(64):
                os.remove(filepath)
                raise Exception("Downloaded video is not a valid MP4")

        return filename, "video"

    raise Exception("Pinterest returned non-video data")

def download_pinterest(url, file_id):

    final_url, page = resolve_pinterest(url)

    pin_id = get_pin_id(final_url, page)

    if not pin_id:
        raise Exception("Pinterest Pin ID not found")

    # 1. Official Pinterest data
    try:
        data = pinterest_api(pin_id)

        if data:
            videos, images = find_media(data)

            if videos:
                videos.sort(
                    key=lambda x:
                    (x["width"] * x["height"]),
                    reverse=True
                )

                return download_direct(
                    videos[0]["url"],
                    file_id,
                    final_url
                )

            if images:
                images.sort(
                    key=lambda x:
                    (x["width"] * x["height"]),
                    reverse=True
                )

                return download_direct(
                    images[0]["url"],
                    file_id,
                    final_url
                )

    except Exception:
        pass

    # 2. HTML fallback
    images, videos = html_media(page)

    for media_url in videos + images:
        try:
            return download_direct(
                media_url,
                file_id,
                final_url
            )
        except Exception:
            continue

    # 3. yt-dlp fallback for unusual Pinterest pins
    try:
        output = f"downloads/{file_id}.%(ext)s"

        opts = {
            "outtmpl": output,
            "format": "best",
            "quiet": True
        }

        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(
                final_url,
                download=True
            )

            filename = os.path.basename(
                ydl.prepare_filename(info)
            )

        ext = os.path.splitext(filename)[1].lower()

        media_type = (
            "image"
            if ext in {
                ".jpg",
                ".jpeg",
                ".png",
                ".webp",
                ".gif"
            }
            else "video"
        )

        return filename, media_type

    except Exception:
        raise Exception(
            "Pinterest media could not be downloaded"
        )


@app.get("/")
def health():
    return {"status": "running"}


@app.post("/detect")
def detect(req: LinkRequest):
    return {
        "platform": detect_platform(req.url)
    }


@app.post("/download")
def download(req: DownloadRequest):

    platform = detect_platform(req.url)
    file_id = str(uuid.uuid4())

    # Pinterest gets its own extractor FIRST.
    if platform == "pinterest":

        try:
            filename, media_type = download_pinterest(
                req.url,
                file_id
            )

            return {
                "platform": "pinterest",
                "filename": filename,
                "status": "done",
                "type": media_type
            }

        except Exception as e:
            return JSONResponse(
                status_code=200,
                content={
                    "platform": "pinterest",
                    "status": "error",
                    "error": str(e)
                }
            )

    # Other platforms
    output = f"downloads/{file_id}.%(ext)s"

    if platform == "youtube" and req.quality != "best":
        height = req.quality.replace("p", "")

        fmt = (
            f"bestvideo[height<={height}]+bestaudio/"
            f"best[height<={height}]/best"
        )
    else:
        fmt = "best"

    opts = {
        "outtmpl": output,
        "format": fmt,
        "quiet": True
    }

    if platform == "youtube":
        opts["extractor_args"] = {
            "youtube": {
                "player_client": ["android", "web"]
            }
        }

        opts["http_headers"] = {
            "User-Agent":
            "com.google.android.youtube/19.09.37"
        }

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:

            info = ydl.extract_info(
                req.url,
                download=True
            )

            filename = os.path.basename(
                ydl.prepare_filename(info)
            )

        return {
            "platform": platform,
            "filename": filename,
            "status": "done",
            "type": "video"
        }

    except Exception as e:

        return JSONResponse(
            status_code=200,
            content={
                "platform": platform,
                "status": "error",
                "error": str(e)
            }
        )


@app.get("/file/{filename}")
def get_file(filename: str):

    path = os.path.join(
        "downloads",
        filename
    )

    lower = filename.lower()

    if lower.endswith(".png"):
        media_type = "image/png"

    elif lower.endswith(".webp"):
        media_type = "image/webp"

    elif lower.endswith(
        (".jpg", ".jpeg")
    ):
        media_type = "image/jpeg"

    else:
        media_type = "video/mp4"

    return FileResponse(
        path,
        media_type=media_type,
        filename=filename
    )
