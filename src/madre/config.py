"""Local MADRE installation configuration."""

from __future__ import annotations

import tomllib
from pathlib import Path

from platformdirs import user_data_path
from pydantic import BaseModel, ConfigDict, Field

from madre.adapters.openai import OpenAIChatConfig


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class Settings(StrictModel):
    data_dir: Path = Field(default_factory=lambda: user_data_path("madre", appauthor=False))
    host: str = "127.0.0.1"
    port: int = Field(default=8731, ge=1, le=65535)
    capabilities: dict[str, OpenAIChatConfig] = Field(default_factory=dict)


def load_settings(path: Path) -> Settings:
    path = path.resolve()
    with path.open("rb") as source:
        settings = Settings.model_validate(tomllib.load(source))
    data_dir = settings.data_dir.expanduser()
    if not data_dir.is_absolute():
        data_dir = path.parent / data_dir
    return settings.model_copy(update={"data_dir": data_dir.resolve()})
