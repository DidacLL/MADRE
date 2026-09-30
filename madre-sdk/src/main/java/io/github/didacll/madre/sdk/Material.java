package io.github.didacll.madre.sdk;

import io.github.didacll.madre.sdk.spira.Sensitivity;

/** An actual information representation participating in semantic composition. */
public interface Material {
    /** Sensitivity of this representation, not a mutable label on its source. */
    Sensitivity sensitivity();
}
