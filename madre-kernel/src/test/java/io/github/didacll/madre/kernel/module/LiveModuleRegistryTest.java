package io.github.didacll.madre.kernel.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.directory.ReachabilityQuery;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.AgentDefinition;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class LiveModuleRegistryTest {
    @Test void registryFiltersExactReachabilityAndCoreIsOnlyAnOrdinaryLiveIdentity() {
        ModuleId target = new ModuleId("ordinary.module");
        MaterialTypeId externalType = new MaterialTypeId(new ModuleId("caller.module"), "text");
        OperationId operationId = new OperationId(target, "receive");
        OperationDefinition<String, String> operation = new OperationDefinition<>(operationId,
                "Receive text", OperationVisibility.PUBLIC,
                Map.of(externalType, Privacy.UNKNOWN), Map.of(), Map.of());
        AgentId agentId = new AgentId(target, "interaction");
        ModuleDefinition definition = new ModuleDefinition(target, "1", "Ordinary module",
                Map.<MaterialTypeId, MaterialType<?>>of(), Set.of(externalType),
                Map.of(agentId, new AgentDefinition(agentId, "Interaction", Integrity.I5,
                        Set.of(), Map.of(), Set.of(operationId))),
                Map.of(), Map.of(operationId, operation));
        LiveModuleRegistry registry = new LiveModuleRegistry(target);
        var registration = registry.register(definition);
        assertEquals(1, registry.reachable(new ReachabilityQuery(
                new ModuleId("caller.module"), externalType, Sensitivity.S2)).size());
        assertTrue(registry.reachable(new ReachabilityQuery(
                new ModuleId("caller.module"), externalType, Sensitivity.S3)).isEmpty());
        assertEquals(target, registry.resolvedCore().orElseThrow());
        registration.close();
        assertTrue(registry.resolvedCore().isEmpty());
    }
}
