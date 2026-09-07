"""Small runtime configuration surface; provider semantics stay in adapters."""

from __future__ import annotations

import os
import tomllib
from pathlib import Path
from typing import Literal
from urllib.parse import urlsplit

from platformdirs import user_data_path
from pydantic import BaseModel, ConfigDict, Field, field_validator

from madre.security import OrdinarySecurityLevel, SecurityLevel


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class CapabilityConfig(StrictModel):
    kind: Literal["openai_chat"] = "openai_chat"
    endpoint: str
    model: str = Field(min_length=1)
    boundary: Literal["local", "remote"] = "local"
    heavyweight: bool = True
    trust: OrdinarySecurityLevel = SecurityLevel.LEVEL_4
    risk: OrdinarySecurityLevel = SecurityLevel.LEVEL_2
    max_input_sensitivity: OrdinarySecurityLevel = SecurityLevel.LEVEL_4

    @field_validator("endpoint")
    @classmethod
    def valid_endpoint(cls, value: str) -> str:
        parsed = urlsplit(value)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            raise ValueError("endpoint must be an HTTP(S) base URL")
        if parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise ValueError("endpoint must not contain credentials/query/fragment")
        _ = parsed.port
        return value.rstrip("/")


class Settings(StrictModel):
    data_dir: Path = Field(default_factory=lambda: user_data_path("madre", appauthor=False))
    host: Literal["127.0.0.1", "::1"] = "127.0.0.1"
    port: int = Field(default=8731, ge=1, le=65535)
    token_env: str = Field(default="MADRE_API_TOKEN", min_length=1)
    capabilities: dict[str, CapabilityConfig] = Field(default_factory=dict)

    def token(self) -> str:
        token = os.environ.get(self.token_env, "")
        if not token.strip():
            raise ValueError(f"required credential environment variable is unset: {self.token_env}")
        return token


def load_settings(path: Path) -> Settings:
    path = path.resolve()
    with path.open("rb") as source:
        settings = Settings.model_validate(tomllib.load(source))
    data_dir = settings.data_dir.expanduser()
    if not data_dir.is_absolute():
        data_dir = path.parent / data_dir
    return settings.model_copy(update={"data_dir": data_dir.resolve()})
