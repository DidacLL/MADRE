"""MADRE governed execution and interoperability platform."""

from madre.broker import Broker
from madre.capabilities import CapabilityRegistry
from madre.interfaces import (
    AgentEndpoint,
    Discovery,
    DurableWorkSubmission,
    MaterialResolver,
    ModuleRegistration,
    OperationEndpoint,
    TransientInference,
    WorkInspection,
    WorkResultAccess,
)
from madre.registry import InteroperabilityRegistry
from madre.runtime import WorkRuntime

__all__ = [
    "AgentEndpoint",
    "Broker",
    "CapabilityRegistry",
    "Discovery",
    "DurableWorkSubmission",
    "InteroperabilityRegistry",
    "MaterialResolver",
    "ModuleRegistration",
    "OperationEndpoint",
    "TransientInference",
    "WorkInspection",
    "WorkResultAccess",
    "WorkRuntime",
]
