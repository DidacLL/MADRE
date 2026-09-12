"""MADRE Kernel physical execution boundary."""

from madre.capabilities import (
    CapabilityAdapter,
    CapabilityDefinition,
    CapabilityError,
    CapabilityId,
    CapabilityInput,
    CapabilityInputs,
    CapabilityRegistry,
    CapabilitySelection,
    CapabilityUnavailable,
    CapacityResourceCoordinator,
    DeterministicCapabilitySelection,
    ResourceClaim,
    ResourceCoordinator,
    ResourceId,
)
from madre.registry import ModuleRegistry
from madre.runtime import Kernel
from madre.storage import WorkQueueStore, open_database
from madre.work import (
    AttemptStatus,
    DeliveryStatus,
    WorkAttempt,
    WorkFailure,
    WorkId,
    WorkRecord,
    WorkStatus,
)

__all__ = [
    "CapacityResourceCoordinator",
    "CapabilityAdapter",
    "CapabilityDefinition",
    "CapabilityError",
    "CapabilityId",
    "CapabilityInput",
    "CapabilityInputs",
    "CapabilityRegistry",
    "CapabilitySelection",
    "CapabilityUnavailable",
    "DeterministicCapabilitySelection",
    "Kernel",
    "ModuleRegistry",
    "ResourceClaim",
    "ResourceCoordinator",
    "ResourceId",
    "AttemptStatus",
    "DeliveryStatus",
    "WorkAttempt",
    "WorkFailure",
    "WorkId",
    "WorkQueueStore",
    "WorkRecord",
    "WorkStatus",
    "open_database",
]
