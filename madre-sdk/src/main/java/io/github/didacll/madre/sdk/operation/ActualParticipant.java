package io.github.didacll.madre.sdk.operation;

import io.github.didacll.madre.algebra.Integrity;
import java.util.Objects;

/** Integrity of one real participant identified by that participant's own identity. */
public record ActualParticipant<I>(I participantIdentity, Integrity integrity) {
    public ActualParticipant { Objects.requireNonNull(participantIdentity, "participantIdentity"); Objects.requireNonNull(integrity, "integrity"); }
}
