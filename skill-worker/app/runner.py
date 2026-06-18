import json
import os
import shutil
import subprocess
import time
import uuid
from pathlib import Path
from typing import Any

import yaml

from app.schemas import RunSkillRequest, RunSkillResponse


class SkillRunnerError(RuntimeError):
    pass


class SkillRunner:
    def __init__(self) -> None:
        self.skills_dir = Path(os.getenv("SKILL_WORKER_SKILLS_DIR", "/app/skills"))
        self.jobs_dir = Path(os.getenv("SKILL_WORKER_JOBS_DIR", "/tmp/rag-study-skill-jobs"))
        self.default_timeout_seconds = int(os.getenv("SKILL_WORKER_TIMEOUT_SECONDS", "1800"))
        self.max_log_chars = int(os.getenv("SKILL_WORKER_MAX_LOG_CHARS", "20000"))
        self.max_result_bytes = int(os.getenv("SKILL_WORKER_MAX_RESULT_BYTES", str(5 * 1024 * 1024)))
        self.jobs_dir.mkdir(parents=True, exist_ok=True)

    def run(self, request: RunSkillRequest) -> RunSkillResponse:
        start = time.monotonic()
        skill_name = self._safe_name(request.skillName)
        skill_dir = self.skills_dir / skill_name
        if not skill_dir.is_dir():
            raise SkillRunnerError(f"Skill not found: {skill_name}")

        metadata = self._load_metadata(skill_dir)
        entrypoint = metadata.get("entrypoint")
        runtime = metadata.get("runtime", "python")
        if not isinstance(entrypoint, str) or not entrypoint.strip():
            raise SkillRunnerError(f"Skill {skill_name} missing entrypoint")
        if runtime != "python":
            raise SkillRunnerError(f"Unsupported runtime: {runtime}")

        configured_timeout = metadata.get("timeoutSeconds", self.default_timeout_seconds)
        timeout_seconds = request.timeoutSeconds or int(configured_timeout)
        timeout_seconds = max(1, min(timeout_seconds, 7200))

        job_dir = self.jobs_dir / f"{skill_name}-{uuid.uuid4().hex}"
        work_dir = job_dir / "work"
        shutil.copytree(skill_dir, work_dir)

        input_path = job_dir / "input.json"
        output_path = job_dir / "result.json"
        input_path.write_text(json.dumps(request.input, ensure_ascii=False), encoding="utf-8")

        command = [
            "python",
            str(work_dir / entrypoint),
            "--input",
            str(input_path),
            "--output",
            str(output_path),
            "--work-dir",
            str(job_dir),
        ]

        env = os.environ.copy()
        env["PYTHONUNBUFFERED"] = "1"
        env["SKILL_JOB_DIR"] = str(job_dir)

        try:
            completed = subprocess.run(
                command,
                cwd=work_dir,
                env=env,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=timeout_seconds,
                check=False,
            )
            logs = self._truncate(completed.stdout or "")
            elapsed = round(time.monotonic() - start, 3)

            if completed.returncode != 0:
                return RunSkillResponse(
                    success=False,
                    skillName=skill_name,
                    result=None,
                    logs=logs,
                    error=f"Skill exited with code {completed.returncode}",
                    elapsedSeconds=elapsed,
                )

            result = self._read_result(output_path)
            return RunSkillResponse(
                success=True,
                skillName=skill_name,
                result=result,
                logs=logs,
                error=None,
                elapsedSeconds=elapsed,
            )
        except subprocess.TimeoutExpired as exc:
            elapsed = round(time.monotonic() - start, 3)
            logs = self._truncate((exc.stdout or "") if isinstance(exc.stdout, str) else "")
            return RunSkillResponse(
                success=False,
                skillName=skill_name,
                result=None,
                logs=logs,
                error=f"Skill timed out after {timeout_seconds} seconds",
                elapsedSeconds=elapsed,
            )
        finally:
            if os.getenv("SKILL_WORKER_KEEP_JOBS", "false").lower() != "true":
                shutil.rmtree(job_dir, ignore_errors=True)

    def _load_metadata(self, skill_dir: Path) -> dict[str, Any]:
        metadata_path = skill_dir / "skill.yaml"
        if not metadata_path.is_file():
            raise SkillRunnerError(f"Skill metadata not found: {metadata_path.name}")
        with metadata_path.open("r", encoding="utf-8") as file:
            data = yaml.safe_load(file) or {}
        if not isinstance(data, dict):
            raise SkillRunnerError("Skill metadata must be a YAML object")
        return data

    def _read_result(self, output_path: Path) -> dict[str, Any]:
        if not output_path.is_file():
            raise SkillRunnerError("Skill completed but did not write result.json")
        if output_path.stat().st_size > self.max_result_bytes:
            raise SkillRunnerError("Skill result is too large")
        with output_path.open("r", encoding="utf-8") as file:
            data = json.load(file)
        if not isinstance(data, dict):
            raise SkillRunnerError("Skill result must be a JSON object")
        return data

    def _truncate(self, logs: str) -> str:
        if len(logs) <= self.max_log_chars:
            return logs
        return logs[-self.max_log_chars:]

    def _safe_name(self, name: str) -> str:
        normalized = name.strip()
        if not normalized or any(char in normalized for char in "\\/.."):
            raise SkillRunnerError("Invalid skill name")
        return normalized
