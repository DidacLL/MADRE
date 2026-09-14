package io.github.didacll.madre.kernel.module;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.sdk.directory.ModuleDirectory;
import io.github.didacll.madre.sdk.directory.ReachabilityQuery;
import io.github.didacll.madre.sdk.directory.ReachableAgent;
import io.github.didacll.madre.sdk.directory.ReachableModule;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.module.AgentDefinition;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
import io.github.didacll.madre.sdk.registration.ModuleRegistration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory registry for currently running Modules; it deliberately has no persistence. */
public final class LiveModuleRegistry implements ModuleRegistration, ModuleDirectory {
    private final ModuleId configuredCore;
    private final ConcurrentHashMap<ModuleId, Entry> entries = new ConcurrentHashMap<>();

    public LiveModuleRegistry(ModuleId configuredCore) {
        this.configuredCore = Objects.requireNonNull(configuredCore, "configuredCore");
    }

    @Override public Registration register(ModuleDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        Entry entry = new Entry(definition);
        if (entries.putIfAbsent(definition.id(), entry) != null) {
            throw new IllegalStateException("Module is already registered: " + definition.id());
        }
        return new Registration() {
            private boolean open = true;
            @Override public ModuleDefinition definition() { return definition; }
            @Override public synchronized void close() { if (open) { entries.remove(definition.id(), entry); open = false; } }
        };
    }

    @Override public List<ReachableModule> reachable(ReachabilityQuery query) {
        Objects.requireNonNull(query, "query");
        List<ReachableModule> result = new ArrayList<>();
        entries.values().stream().map(Entry::definition).sorted(java.util.Comparator.comparing(module -> module.id().value())).forEach(module -> {
            Map<OperationId, OperationDefinition<?, ?>> operations = new HashMap<>();
            module.operations().forEach((id, operation) -> {
                Privacy privacy = operation.acceptedMaterial().get(query.materialType());
                if (operation.visibility() == OperationVisibility.PUBLIC && privacy != null && query.sensitivity().canReach(privacy)) operations.put(id, operation);
            });
            if (operations.isEmpty()) return;
            Map<AgentId, ReachableAgent> agents = new HashMap<>();
            for (AgentDefinition agent : module.agents().values()) {
                java.util.Set<OperationId> exposed = agent.operations().stream().filter(operations::containsKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
                if (!exposed.isEmpty()) {
                    Privacy effective = exposed.stream().flatMap(id -> operations.get(id).acceptedMaterial().values().stream()).reduce(Privacy.SECRET, Privacy::combine);
                    agents.put(agent.id(), new ReachableAgent(agent.id(), agent.purpose(), exposed, effective));
                }
            }
            result.add(new ReachableModule(module.id(), module.version(), module.purpose(), agents, operations));
        });
        return List.copyOf(result);
    }

    public Optional<ModuleId> resolvedCore() {
        return entries.containsKey(configuredCore) ? Optional.of(configuredCore) : Optional.empty();
    }

    private record Entry(ModuleDefinition definition) { }
}
