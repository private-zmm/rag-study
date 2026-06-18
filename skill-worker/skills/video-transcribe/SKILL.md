---
name: video-transcribe
description: Download online videos with yt-dlp, extract audio with FFmpeg, transcribe with faster-whisper, and return Markdown.
args: url, platform, language
version: 0.1.0
---

# Video Transcribe

This local skill receives a video URL, downloads the media with `yt-dlp`,
extracts 16 kHz mono WAV audio with `ffmpeg`, transcribes speech with
`faster-whisper`, and returns structured Markdown for saving into a knowledge
base.
