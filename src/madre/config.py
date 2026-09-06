"""Explicit configuration, independent of command-line or service lifecycle."""

import ipaddress
import os
import tomllib
from pathlib import Path
from typing import Literal
from urllib.parse import urlsplit

from platformdirs import user_data_path
from pydantic import BaseModel, ConfigDict, Field, field_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class CapabilityConfig(StrictModel):
    kind: Literal["chat_completions"] = "chat_completions"
    endpoint: str
    model: str = Field(min_length=1)
    boundary: Literal["local", "remote"] = "local"
    api_key_env: str | None = None

    @field_validator("endpoint")
    @classmethod
    def valid_endpoint(cls, value: str) -> str:
        parsed = urlsplit(value)
        if (
            parsed.scheme not in {"http", "https"}
            or not parsed.hostname
            or parsed.username
            or parsed.password
            or parsed.query
            or parsed.fragment
        ):
            raise ValueError(
                "endpoint must be an HTTP(S) base URL without credentials/query/fragment"
            )
        _ = parsed.port  # Validate malformed ports as configuration errors.
        return value.rstrip("/")


class Settings(StrictModel):
    data_dir: Path = Field(default_factory=lambda: user_data_path("madre", appauthor=False))
    host: Literal["127.0.0.1", "::1"] = "127.0.0.1"
    port: int = Field(default=8731, ge=1, le=65535)
    token_env: str = Field(default="MADRE_API_TOKEN", min_length=1)
    capabilities: dict[str, CapabilityConfig] = Field(default_factory=dict)

    def token(self) -> str:
        value = os.environ.get(self.token_env, "")
        if not value.strip():
            raise ValueError(f"required credential environment variable is unset: {self.token_env}")
        return value


def load_settings(path: Path) -> Settings:
    path = path.resolve()
    with path.open("rb") as source:
        settings = Settings.model_validate(tomllib.load(source))
    data_dir = settings.data_dir.expanduser()
    if not data_dir.is_absolute():
        data_dir = path.parent / data_dir
    return settings.model_copy(update={"data_dir": data_dir.resolve()})


def is_loopback_endpoint(endpoint: str) -> bool:
    """Require a literal address, avoiding hostname resolution ambiguity."""
    try:
        return ipaddress.ip_address(urlsplit(endpoint).hostname or "").is_loopback
    except ValueError:
        return False
