import argparse
import json
import os
import re
import subprocess
from pathlib import Path
from typing import Any

from faster_whisper import WhisperModel


def main() -> None:
    args = parse_args()
    input_data = read_json(args.input)
    output_path = Path(args.output)
    work_dir = Path(args.work_dir)
    media_dir = work_dir / "media"
    media_dir.mkdir(parents=True, exist_ok=True)

    url = str(input_data.get("url") or "").strip()
    if not url:
        raise SystemExit("input.url is required")

    language = normalize_language(input_data.get("language"))
    platform = str(input_data.get("platform") or detect_platform(url))
    keep_timestamps = bool(input_data.get("keepTimestamps", True))
    model_size = str(input_data.get("modelSize") or os.getenv("WHISPER_MODEL_SIZE", "small"))

    metadata = probe_video(url)
    print(f"video id: {metadata.get('id', '')}")
    print(f"title: {metadata.get('title', '')}")
    print(f"platform: {platform}")

    video_path = media_dir / "video.%(ext)s"
    run_command([
        "yt-dlp",
        "--no-playlist",
        "-o",
        str(video_path),
        url,
    ])

    downloaded_video = find_downloaded_video(media_dir)
    audio_path = media_dir / "audio.wav"
    run_command([
        "ffmpeg",
        "-i",
        str(downloaded_video),
        "-ar",
        "16000",
        "-ac",
        "1",
        "-c:a",
        "pcm_s16le",
        str(audio_path),
        "-y",
    ])

    transcript = transcribe(audio_path, language, model_size)
    markdown = render_markdown(url, platform, metadata, transcript, keep_timestamps)

    result = {
        "title": safe_title(metadata.get("title") or "视频转写"),
        "platform": platform,
        "sourceUrl": url,
        "author": metadata.get("uploader") or metadata.get("channel") or "",
        "duration": metadata.get("duration"),
        "uploadDate": metadata.get("upload_date") or "",
        "plainText": transcript["plainText"],
        "timestampText": transcript["timestampText"],
        "markdown": markdown,
    }
    output_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--work-dir", required=True)
    return parser.parse_args()


def read_json(path: str) -> dict[str, Any]:
    with open(path, "r", encoding="utf-8") as file:
        data = json.load(file)
    if not isinstance(data, dict):
        raise SystemExit("input must be a JSON object")
    return data


def probe_video(url: str) -> dict[str, Any]:
    completed = run_command([
        "yt-dlp",
        "--dump-single-json",
        "--no-playlist",
        "--skip-download",
        url,
    ], echo_output=False)
    try:
        data = json.loads(completed.stdout)
    except json.JSONDecodeError:
        return {}
    return data if isinstance(data, dict) else {}


def run_command(command: list[str], echo_output: bool = True) -> subprocess.CompletedProcess[str]:
    print("$ " + " ".join(command[:2]))
    completed = subprocess.run(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if echo_output and completed.stdout:
        print(completed.stdout[-4000:])
    if completed.returncode != 0:
        raise SystemExit(f"command failed with code {completed.returncode}: {command[0]}")
    return completed


def find_downloaded_video(media_dir: Path) -> Path:
    video_extensions = {".mp4", ".m4v", ".webm", ".mkv", ".mov", ".flv"}
    candidates = [
        path
        for path in media_dir.iterdir()
        if path.is_file() and path.suffix.lower() in video_extensions
    ]
    if not candidates:
        raise SystemExit("yt-dlp did not produce a video file")
    return max(candidates, key=lambda path: path.stat().st_size)


def transcribe(audio_path: Path, language: str | None, model_size: str) -> dict[str, Any]:
    print(f"loading faster-whisper model: {model_size}")
    model = WhisperModel(model_size, device="auto", compute_type="auto")
    kwargs: dict[str, Any] = {}
    if language:
        kwargs["language"] = language
    segments_iter, info = model.transcribe(str(audio_path), **kwargs)
    print(f"detected language: {getattr(info, 'language', '')}")

    segments = list(segments_iter)
    timestamp_lines = [
        f"[{segment.start:.1f}s -> {segment.end:.1f}s] {segment.text.strip()}"
        for segment in segments
        if segment.text and segment.text.strip()
    ]
    plain_lines = [
        segment.text.strip()
        for segment in segments
        if segment.text and segment.text.strip()
    ]
    return {
        "timestampText": "\n".join(timestamp_lines),
        "plainText": "\n".join(plain_lines),
    }


def render_markdown(
    url: str,
    platform: str,
    metadata: dict[str, Any],
    transcript: dict[str, Any],
    keep_timestamps: bool,
) -> str:
    title = safe_title(metadata.get("title") or "视频转写")
    author = metadata.get("uploader") or metadata.get("channel") or ""
    duration = metadata.get("duration")
    upload_date = metadata.get("upload_date") or ""
    timestamp_text = transcript["timestampText"]
    plain_text = transcript["plainText"]

    front_matter = [
        "---",
        f"source: {platform}",
        f"url: {url}",
        f"author: {author}",
        f"date: {upload_date}",
    ]
    if duration is not None:
        front_matter.append(f"duration: {duration}s")
    front_matter.extend(["tags: [视频转写]", "---", ""])

    sections = [
        "\n".join(front_matter),
        f"# {title}",
        f"**来源：** [{platform}]({url})"
        + (f" | **作者：** {author}" if author else "")
        + (f" | **时长：** {duration}s" if duration is not None else ""),
        "---",
        "## 文字稿",
        plain_text or "（未识别到语音文字）",
    ]

    if keep_timestamps and timestamp_text:
        sections.extend(["", "## 带时间戳字幕", timestamp_text])
    return "\n\n".join(sections).strip() + "\n"


def normalize_language(value: Any) -> str | None:
    if value is None:
        return None
    language = str(value).strip()
    if not language or language.lower() == "auto":
        return None
    return language


def detect_platform(url: str) -> str:
    lowered = url.lower()
    if "douyin.com" in lowered:
        return "douyin"
    if "bilibili.com" in lowered or "b23.tv" in lowered:
        return "bilibili"
    if "youtube.com" in lowered or "youtu.be" in lowered:
        return "youtube"
    return "video"


def safe_title(title: str) -> str:
    normalized = re.sub(r"\s+", " ", title).strip()
    return normalized[:120] if normalized else "视频转写"


if __name__ == "__main__":
    main()
