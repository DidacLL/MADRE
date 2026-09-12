"""Typed local installation configuration."""

from __future__ import annotations

import tomllib
from pathlib import Path

from platformdirs import user_data_path
from pydantic import BaseModel, ConfigDict, Field

from madre.adapters.openai import OpenAIChatConfig, OpenAICompatibleChatCapability
from madre.capabilities import (
    CapabilityDefinition,
    CapabilityId,
    CapabilityInput,
    CapabilityInputs,
    CapabilityRegistry,
    ResourceClaim,
    ResourceId,
)
from madre_sdk import (
    ComputationId,
    ExecutionLocation,
    LatencyClass,
    MaterialType,
    MaterialTypeId,
    ModuleId,
    PhysicalProperties,
    Privacy,
)


class _ConfigModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class MaterialTypeConfig(_ConfigModel):
    module: str
    module_revision: str = "1"
    name: str
    revision: str = "1"
    media_type: str

    def material_type(self) -> MaterialType[object]:
        module = ModuleId(self.module, self.module_revision)
        return MaterialType[object](
            MaterialTypeId(module, self.name, self.revision),
            self.media_type,
        )


class CapabilityInputConfig(_ConfigModel):
    material_type: MaterialTypeConfig
    privacy: Privacy


class ResourceClaimConfig(_ConfigModel):
    name: str
    units: int = Field(default=1, ge=1)


class InstalledOpenAIChat(_ConfigModel):
    name: str
    revision: str = "1"
    computation_namespace: str
    computation_name: str
    computation_revision: str = "1"
    inputs: tuple[CapabilityInputConfig, ...]
    output_type: MaterialTypeConfig
    location: ExecutionLocation
    latency: LatencyClass = LatencyClass.STANDARD
    resources: tuple[ResourceClaimConfig, ...] = ()
    adapter: OpenAIChatConfig

    def install(self) -> OpenAICompatibleChatCapability:
        definition = CapabilityDefinition(
            identity=CapabilityId(self.name, self.revision),
            computation=ComputationId(
                self.computation_namespace,
                self.computation_name,
                self.computation_revision,
            ),
            output_type=self.output_type.material_type(),
            inputs=CapabilityInputs(
                tuple(
                    CapabilityInput(item.material_type.material_type().identity, item.privacy)
                    for item in self.inputs
                )
            ),
            properties=PhysicalProperties(self.location, self.latency),
            resources=tuple(
                ResourceClaim(ResourceId(resource.name), resource.units)
                for resource in self.resources
            ),
        )
        return OpenAICompatibleChatCapability(definition, self.adapter)


class Settings(_ConfigModel):
    data_dir: Path = Field(default_factory=lambda: user_data_path("madre", appauthor=False))
    host: str = "127.0.0.1"
    port: int = Field(default=8731, ge=1, le=65535)
    openai_chat: tuple[InstalledOpenAIChat, ...] = ()

    def capabilities(self) -> CapabilityRegistry:
        registry = CapabilityRegistry()
        for installed in self.openai_chat:
            registry.register(installed.install())
        return registry


def load_settings(path: Path) -> Settings:
    location = path.resolve()
    with location.open("rb") as source:
        settings = Settings.model_validate(tomllib.load(source))
    data_dir = settings.data_dir.expanduser()
    if not data_dir.is_absolute():
        data_dir = location.parent / data_dir
    return settings.model_copy(update={"data_dir": data_dir.resolve()})
