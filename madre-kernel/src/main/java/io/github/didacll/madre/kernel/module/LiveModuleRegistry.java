package io.github.didacll.madre.kernel.module;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.sdk.directory.ModuleDirectory;
import io.github.didacll.madre.sdk.directory.ReachabilityQuery;
import io.github.didacll.madre.sdk.directory.ReachableAgent;
import io.github.didacll.madre.sdk.directory.ReachableModule;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.module.AgentDefinition;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
import io.github.didacll.madre.sdk.operation.ModuleInvoker;
import io.github.didacll.madre.sdk.operation.OperationCall;
import io.github.didacll.madre.sdk.operation.OwnerModuleInvoker;
import io.github.didacll.madre.sdk.registration.ModuleRegistration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory registry for currently running executable Modules; it deliberately has no persistence. */
public final class LiveModuleRegistry
        implements ModuleRegistration, ModuleDirectory, ModuleInvoker, OwnerModuleInvoker {
    private final Optional<ModuleId> configuredCore;
    private final ConcurrentHashMap<ModuleId, Entry> entries = new ConcurrentHashMap<>();

    public LiveModuleRegistry() {
        this(Optional.empty());
    }

    public LiveModuleRegistry(ModuleId configuredCore) {
        this(Optional.of(Objects.requireNonNull(configuredCore, "configuredCore")));
    }

    public LiveModuleRegistry(Optional<ModuleId> configuredCore) {
        this.configuredCore = Objects.requireNonNull(configuredCore, "configuredCore");
    }

    @Override public Registration register(ModuleInstance instance) {
        ModuleInstance executable = Objects.requireNonNull(instance, "instance");
        executable.validateBindings();
        Entry entry = new Entry(executable);
        ModuleId moduleId = executable.definition().id();
        if (entries.putIfAbsent(moduleId, entry) != null) {
            throw new IllegalStateException("Module is already registered: " + moduleId);
        }
        return new Registration() {
            private boolean open = true;
            @Override public ModuleInstance instance() { return executable; }
            @Override public synchronized void close() {
                if (open) {
                    entries.remove(moduleId, entry);
                    open = false;
                }
            }
        };
    }

    @Override public List<ReachableModule> reachable(ReachabilityQuery query) {
        Objects.requireNonNull(query, "query");
        List<ReachableModule> result = new ArrayList<>();
        entries.values().stream().map(Entry::definition)
                .sorted(Comparator.comparing(module -> module.id().value())).forEach(module -> {
                    Map<OperationId, OperationDefinition<?, ?>> operations = new HashMap<>();
                    module.operations().forEach((id, operation) -> {
                        Privacy privacy = operation.acceptedMaterial().get(query.materialType());
                        if (operation.visibility() == OperationVisibility.PUBLIC && privacy != null
                                && query.sensitivity().canReach(privacy)) {
                            operations.put(id, operation);
                        }
                    });
                    if (operations.isEmpty()) return;
                    Map<AgentId, ReachableAgent> agents = new HashMap<>();
                    for (AgentDefinition agent : module.agents().values()) {
                        java.util.Set<OperationId> exposed = agent.operations().stream()
                                .filter(operations::containsKey)
                                .collect(java.util.stream.Collectors.toUnmodifiableSet());
                        if (!exposed.isEmpty()) {
                            Privacy effective = exposed.stream()
                                    .flatMap(id -> operations.get(id).acceptedMaterial().values().stream())
                                    .reduce(Privacy.SECRET, Privacy::combine);
                            agents.put(agent.id(), new ReachableAgent(agent.id(), agent.purpose(),
                                    exposed, effective));
                        }
                    }
                    result.add(new ReachableModule(module.id(), module.version(), module.purpose(),
                            agents, operations));
                });
        return List.copyOf(result);
    }

    @Override public <I, O> CompletionStage<Material<O>> invokePublic(OperationCall<I, O> call) {
        OperationCall<I, O> requested = Objects.requireNonNull(call, "call");
        OperationBinding<?, ?> binding = exactBinding(requested);
        return invokePublicExact(binding, requested);
    }

    @Override public <I, O> CompletionStage<Material<O>> invokeOwner(OperationCall<I, O> call) {
        OperationCall<I, O> requested = Objects.requireNonNull(call, "call");
        OperationBinding<?, ?> binding = exactBinding(requested);
        if (binding.definition().visibility() != OperationVisibility.PUBLIC) {
            throw new IllegalArgumentException("Operation is not owner-callable: "
                    + requested.operation().id());
        }
        return invokeOwnerExact(binding, requested);
    }

    private OperationBinding<?, ?> exactBinding(OperationCall<?, ?> requested) {
        Entry entry = entries.get(requested.operation().id().moduleId());
        if (entry == null) {
            throw new IllegalArgumentException(
                    "Module is not installed: " + requested.operation().id().moduleId());
        }
        OperationBinding<?, ?> binding = entry.instance().operations().get(
                requested.operation().id());
        if (binding == null || binding.definition() != requested.operation()) {
            throw new IllegalArgumentException(
                    "OperationCall does not identify the exact installed Operation binding");
        }
        return binding;
    }

    @SuppressWarnings("unchecked")
    private static <I, O> CompletionStage<Material<O>> invokePublicExact(
            OperationBinding<?, ?> binding, OperationCall<I, O> call) {
        return ((OperationBinding<I, O>) binding).invokePublic(call);
    }

    @SuppressWarnings("unchecked")
    private static <I, O> CompletionStage<Material<O>> invokeOwnerExact(
            OperationBinding<?, ?> binding, OperationCall<I, O> call) {
        return ((OperationBinding<I, O>) binding).invoke(call);
    }

    /** Returns the installed canonical definition for application-level discovery by identity. */
    public Optional<ModuleDefinition> definition(ModuleId moduleId) {
        Entry entry = entries.get(Objects.requireNonNull(moduleId, "moduleId"));
        return entry == null ? Optional.empty() : Optional.of(entry.definition());
    }

    /** Returns a deterministic snapshot of currently installed Module definitions. */
    public List<ModuleDefinition> definitions() {
        return entries.values().stream().map(Entry::definition)
                .sorted(Comparator.comparing(module -> module.id().value())).toList();
    }

    /** CORE is only an optional installation-role lookup over the ordinary live registry. */
    public Optional<ModuleId> resolvedCore() {
        return configuredCore.filter(entries::containsKey);
    }

    private record Entry(ModuleInstance instance) {
        ModuleDefinition definition() { return instance.definition(); }
    }
}
