"""MADRE's nominal ordered carriers."""

from __future__ import annotations

from enum import Enum
from typing import Self


class _OrderedCarrier(Enum):
    @property
    def rank(self) -> int:
        return int(self.value)

    @classmethod
    def maximum(cls, first: Self, *rest: Self) -> Self:
        if any(type(value) is not cls for value in (first, *rest)):
            raise TypeError(f"{cls.__name__}.maximum accepts only {cls.__name__} values")
        return max((first, *rest), key=lambda value: value.rank)

    @classmethod
    def minimum(cls, first: Self, *rest: Self) -> Self:
        if any(type(value) is not cls for value in (first, *rest)):
            raise TypeError(f"{cls.__name__}.minimum accepts only {cls.__name__} values")
        return min((first, *rest), key=lambda value: value.rank)


class Sensitivity(_OrderedCarrier):
    S1 = 1
    S2 = 2
    S3 = 3
    S4 = 4
    S5 = 5


class Privacy(_OrderedCarrier):
    P1 = 1
    P2 = 2
    P3 = 3
    P4 = 4
    P5 = 5

    PUBLIC = 1
    UNKNOWN = 2


class Integrity(_OrderedCarrier):
    I1 = 1
    I2 = 2
    I3 = 3
    I4 = 4
    I5 = 5


class Risk(_OrderedCarrier):
    R1 = 1
    R2 = 2
    R3 = 3
    R4 = 4
    R5 = 5


class Autonomy(_OrderedCarrier):
    A1 = 1
    A2 = 2
    A3 = 3
    A4 = 4
    A5 = 5
