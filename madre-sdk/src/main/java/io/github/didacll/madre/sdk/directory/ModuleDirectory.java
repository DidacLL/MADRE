package io.github.didacll.madre.sdk.directory;

import java.util.List;

/** Read-only live directory of exact currently reachable Module behavior. */
public interface ModuleDirectory {
    List<ReachableModule> reachable(ReachabilityQuery query);
}
