FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    SKILL_WORKER_SKILLS_DIR=/app/skills \
    SKILL_WORKER_JOBS_DIR=/tmp/rag-study-skill-jobs

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY skill-worker/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

COPY skill-worker/app /app/app
COPY skill-worker/skills /app/skills

RUN useradd --create-home --shell /usr/sbin/nologin worker
USER worker

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
