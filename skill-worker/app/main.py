from fastapi import FastAPI, HTTPException

from app.schemas import RunSkillRequest, RunSkillResponse
from app.runner import SkillRunner, SkillRunnerError

app = FastAPI(title="Rag Study Skill Worker", version="0.1.0")
runner = SkillRunner()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/run", response_model=RunSkillResponse)
def run_skill(request: RunSkillRequest) -> RunSkillResponse:
    try:
        return runner.run(request)
    except SkillRunnerError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
