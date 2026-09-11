"""Compile-only regression: equal ranks must never make security facets assignable."""

from madre.security import Autonomy, Integrity, Privacy, Risk, Sensitivity

sensitivity: Sensitivity = Sensitivity.S1
privacy: Privacy = Privacy.PUBLIC
integrity: Integrity = Integrity.I1
risk: Risk = Risk.R1
autonomy: Autonomy = Autonomy.A1

wrong_sensitivity: Sensitivity = Privacy.PUBLIC  # type: ignore[assignment]
wrong_privacy: Privacy = Integrity.I1  # type: ignore[assignment]
wrong_integrity: Integrity = Risk.R1  # type: ignore[assignment]
wrong_risk: Risk = Autonomy.A1  # type: ignore[assignment]
wrong_autonomy: Autonomy = Sensitivity.S1  # type: ignore[assignment]
