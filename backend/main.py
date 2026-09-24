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


def resolve_pinterest_url(url):
    r = requests.get(
        url,
        headers=PIN_HEADERS,
        allow_redirects=True,
        timeout=25,
    )
    r.raise_for_status()
    return r.url, r.text


def extract_pin_id(url, page_html=""):
    # Normal Pinterest pin URL
    match = re.search(
        r"/pin/(?:[^/?#]+--)?(\d+)",
        url,
        re.I,
    )

    if match:
        return match.group(1)

    # Try to find a Pinterest pin id inside the HTML
    patterns = [
        r'"pinId"\s*:\s*"(\d+)"',
        r'"pin_id"\s*:\s*"(\d+)"',
        r'"id"\s*:\s*"(\d{12,})"',
    ]

    for pattern in patterns:
        match = re.search(pattern, page_html)
        if match:
            return match.group(1)

    return None


def pinterest_api_data(pin_id):
    api_url = "https://www.pinterest.com/resource/PinResource/get/"

    options = {
        "field_set_key": "unauth_react_main_pin",
        "id": pin_id,
    }

    params = {
        "data": json.dumps({"options": options})
    }

    headers = {
        **PIN_HEADERS,
        "X-Pinterest-PWS-Handler": "www/[username].js",
    }

    r = requests.get(
        api_url,
        params=params,
        headers=headers,
        timeout=25,
    )

    r.raise_for_status()

    payload = r.json()

    return payload.get("resource_response", {}).get("data")


def collect_video_urls(obj):
    found = []

    if isinstance(obj, dict):
        for key, value in obj.items():

            if key == "video_list" and isinstance(value, dict):
                for item in value.values():
                    if isinstance(item, dict):
                        media_url = item.get("url")

                        if media_url:
                            found.append({
                                "url": media_url,
                                "width": item.get("width") or 0,
                                "height": item.get("height") or 0,
                                "duration": item.get("duration") or 0,
                            })

            found.extend(collect_video_urls(value))

    elif isinstance(obj, list):
        for item in obj:
            found.extend(collect_video_urls(item))

    return found


def collect_image_urls(obj):
    found = []

    if isinstance(obj, dict):
        images = obj.get("images")

        if isinstance(images, dict):
            for item in images.values():
                if isinstance(item, dict):
                    media_url = item.get("url")

                    if media_url:
                        found.append({
                            "url": media_url,
                            "width": item.get("width") or 0,
                            "height": item.get("height") or 0,
                        })

        for value in obj.values():
            found.extend(collect_image_urls(value))

    elif isinstance(obj, list):
        for item in obj:
            found.extend(collect_image_urls(item))

    return found


def extract_html_media(page_html):
    images = []
    videos = []

    # Meta tags
    for tag in re.findall(r"<meta\b[^>]*>", page_html, re.I):
        attrs = dict(
            re.findall(
                r"""([:\w-]+)\s*=\s*["']([^"']*)["']""",
                tag,
                re.I,
            )
        )

        prop = (
            attrs.get("property")
            or attrs.get("name")
            or ""
        ).lower()

        content = attrs.get("content")

        if not content:
            continue

        content = html.unescape(content)

        if prop in {
            "og:image",
            "og:image:url",
            "twitter:image",
            "twitter:image:src",
        }:
            images.append(content)

        elif prop in {
            "og:video",
            "og:video:url",
            "og:video:secure_url",
            "twitter:player:stream",
        }:
            videos.append(content)

    # Pinterest sometimes keeps direct media URLs inside JSON/script data.
    cleaned = html.unescape(page_html).replace("\\/", "/")

    video_patterns = re.findall(
        r'https?://[^"\']+?\.(?:mp4|m4v)(?:\?[^"\']*)?',
        cleaned,
        re.I,
    )

    image_patterns = re.findall(
        r'https?://[^"\']+?i\.pinimg\.com[^"\']+?\.(?:jpg|jpeg|png|webp)(?:\?[^"\']*)?',
        cleaned,
        re.I,
    )

    videos.extend(video_patterns)
    images.extend(image_patterns)

    return list(dict.fromkeys(images)), list(dict.fromkeys(videos))


def choose_best_video(videos):
    if not videos:
        return None

    direct = [
        item for item in videos
        if not item["url"].lower().split("?")[0].endswith(".m3u8")
    ]

    if direct:
        videos = direct

    return max(
        videos,
        key=lambda x: (
            (x.get("width") or 0) * (x.get("height") or 0),
            x.get("duration") or 0,
        ),
    )["url"]


def choose_best_image(images):
    if not images:
        return None

    return max(
        images,
        key=lambda x: (
            (x.get("width") or 0) * (x.get("height") or 0)
        ),
    )["url"] if isinstance(images[0], dict) else images[0]


def download_direct_media(media_url, file_id, referer=None):
    headers = {
        "User-Agent": PIN_HEADERS["User-Agent"],
        "Accept": "*/*",
    }

    if referer:
        headers["Referer"] = referer

    r = requests.get(
        media_url,
        headers=headers,
        stream=True,
        timeout=60,
    )

    r.raise_for_status()

    content_type = (
        r.headers.get("content-type", "")
        .lower()
        .split(";")[0]
    )

    url_path = urlparse(media_url).path.lower()

    if "video" in content_type or url_path.endswith(
        (".mp4", ".m4v", ".mov")
    ):
        ext = ".mp4"
        media_type = "video"
    elif "png" in content_type or url_path.endswith(".png"):
        ext = ".png"
        media_type = "image"
    elif "webp" in content_type or url_path.endswith(".webp"):
        ext = ".webp"
        media_type = "image"
    else:
        ext = ".jpg"
        media_type = "image"

    filename = f"{file_id}{ext}"
    path = os.path.join("downloads", filename)

    with open(path, "wb") as f:
        for chunk in r.iter_content(chunk_size=1024 * 1024):
            if chunk:
                f.write(chunk)

    return filename, media_type


def download_pinterest_media(url, file_id):
    final_url, page_html = resolve_pinterest_url(url)

    pin_id = extract_pin_id(final_url, page_html)

    if not pin_id:
        raise Exception("Could not identify Pinterest Pin ID")

    # First choice: Pinterest's own public Pin data.
    try:
        data = pinterest_api_data(pin_id)

        if data:
            videos = collect_video_urls(data)

            video_url = choose_best_video(videos)

            if video_url:
                return download_direct_media(
                    video_url,
                    file_id,
                    referer=final_url,
                )

            images = collect_image_urls(data)

            image_url = choose_best_image(images)

            if image_url:
                return download_direct_media(
                    image_url,
                    file_id,
                    referer=final_url,
                )

    except Exception:
        pass

    # Second choice: public HTML metadata.
    images, videos = extract_html_media(page_html)

    if videos:
        try:
            return download_direct_media(
                videos[0],
                file_id,
                referer=final_url,
            )
        except Exception:
            pass

    if images:
        try:
            return download_direct_media(
                images[0],
                file_id,
                referer=final_url,
            )
        except Exception:
            pass

    raise Exception("Pinterest media could not be extracted")


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

    # Pinterest gets its own extractor first.
    # This fixes image pins and also handles direct video URLs.
    if platform == "pinterest":
        try:
            filename, media_type = download_pinterest_media(
                req.url,
                file_id,
            )

            return {
                "platform": platform,
                "filename": filename,
                "status": "done",
                "type": media_type,
            }

        except Exception as pinterest_error:
            pinterest_error_message = str(pinterest_error)

            # Keep yt-dlp as a final fallback for unusual Pinterest videos.
            try:
                out_path = f"downloads/{file_id}.%(ext)s"

                ydl_opts = {
                    "outtmpl": out_path,
                    "format": "best",
                    "quiet": True,
                }

                with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                    info = ydl.extract_info(
                        req.url,
                        download=True,
                    )

                    filename = ydl.prepare_filename(info)

                basename = os.path.basename(filename)

                ext = os.path.splitext(basename)[1].lower()

                media_type = (
                    "image"
                    if ext in {
                        ".jpg",
                        ".jpeg",
                        ".png",
                        ".webp",
                        ".gif",
                    }
                    else "video"
                )

                return {
                    "platform": platform,
                    "filename": basename,
                    "status": "done",
                    "type": media_type,
                }

            except Exception:
                return JSONResponse(
                    status_code=200,
                    content={
                        "platform": platform,
                        "status": "error",
                        "error": pinterest_error_message,
                    },
                )

    out_path = f"downloads/{file_id}.%(ext)s"

    if platform == "youtube" and req.quality != "best":
        fmt = (
            f"bestvideo[height<={req.quality[:-1]}]+bestaudio/"
            f"best[height<={req.quality[:-1]}]/best"
        )
    else:
        fmt = "best"

    ydl_opts = {
        "outtmpl": out_path,
        "format": fmt,
        "quiet": True,
    }

    if platform == "youtube":
        ydl_opts["extractor_args"] = {
            "youtube": {
                "player_client": ["android", "web"]
            }
        }

        ydl_opts["http_headers"] = {
            "User-Agent": "com.google.android.youtube/19.09.37"
        }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(
                req.url,
                download=True,
            )

            filename = ydl.prepare_filename(info)

        basename = os.path.basename(filename)

        return {
            "platform": platform,
            "filename": basename,
            "status": "done",
            "type": "video",
        }

    except Exception as e:
        return JSONResponse(
            status_code=200,
            content={
                "platform": platform,
                "status": "error",
                "error": str(e),
            },
        )


@app.get("/file/{filename}")
def get_file(filename: str):
    path = f"downloads/{filename}"

    if filename.lower().endswith(".png"):
        media_type = "image/png"
    elif filename.lower().endswith(".webp"):
        media_type = "image/webp"
    elif filename.lower().endswith((".jpg", ".jpeg")):
        media_type = "image/jpeg"
    else:
        media_type = "video/mp4"

    return FileResponse(
        path,
        media_type=media_type,
        filename=filename,
    )
