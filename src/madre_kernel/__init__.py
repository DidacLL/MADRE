"""MADRE Kernel semantic orchestration package."""

from madre_kernel.agents import CORE_AGENT_DEFINITION, CORE_FINAL_SCHEMA, build_core_module
from madre_kernel.contracts import *  # noqa: F403
from madre_kernel.kernel import (
    DiscoveryPolicy,
    DiscoveryRule,
    Kernel,
    SecurityRejectedError,
    repeat_permitted,
)
from madre_kernel.modules import (
    CALC_INPUT_SCHEMA,
    CALC_MODULE,
    CALC_OBJECTIVE_SCHEMA,
    CALC_OUTPUT_SCHEMA,
    CALC_SCOPE,
    CALCULATE,
    InProcessModule,
    OperationMaterial,
    SchemaCodecRegistry,
    UnknownOperationEffect,
    build_calculator_module,
)
from madre_kernel.runtime_client import KernelRuntimeClient, RuntimeBoundaryError
from madre_kernel.security import KernelSecurityPolicy, SecurityAlgebra
from madre_kernel.storage import KernelStore

__all__ = [
    "CALCULATE",
    "CALC_INPUT_SCHEMA",
    "CALC_MODULE",
    "CALC_OBJECTIVE_SCHEMA",
    "CALC_OUTPUT_SCHEMA",
    "CALC_SCOPE",
    "CORE_AGENT_DEFINITION",
    "CORE_FINAL_SCHEMA",
    "DiscoveryPolicy",
    "DiscoveryRule",
    "InProcessModule",
    "Kernel",
    "KernelRuntimeClient",
    "KernelSecurityPolicy",
    "KernelStore",
    "OperationMaterial",
    "RuntimeBoundaryError",
    "SchemaCodecRegistry",
    "SecurityAlgebra",
    "SecurityRejectedError",
    "UnknownOperationEffect",
    "build_calculator_module",
    "build_core_module",
    "repeat_permitted",
]
