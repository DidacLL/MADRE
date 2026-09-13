package io.github.didacll.madre.sdk.core;

import io.github.didacll.madre.sdk.identity.ModuleId;
import java.util.Optional;

/** Resolves the configured CORE installation role to an ordinary live Module identity. */
public interface CoreRoleResolver {
    Optional<ModuleId> resolvedCore();
}
