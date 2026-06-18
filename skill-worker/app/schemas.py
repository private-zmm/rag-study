from typing import Any

from pydantic import BaseModel, Field


class RunSkillRequest(BaseModel):
    skillName: str = Field(min_length=1, max_length=120)
    input: dict[str, Any] = Field(default_factory=dict)
    timeoutSeconds: int | None = Field(default=None, ge=1, le=7200)


class RunSkillResponse(BaseModel):
    success: bool
    skillName: str
    result: dict[str, Any] | None = None
    logs: str = ""
    error: str | None = None
    elapsedSeconds: float
