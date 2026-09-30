package io.github.didacll.madre.sdk;

import io.github.didacll.madre.sdk.spira.Sensitivity;

/**
 * Actual information/context representation participating in semantic
 * composition. Domain types own their meaning; this is no universal content
 * schema. Transforming or minimising information creates new Material rather
 * than relabelling its source.
 */
public interface Material {
    /** Sensitivity of this representation, not a mutable label on its source. */
    Sensitivity sensitivity();
}
