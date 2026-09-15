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
import io.github.didacll.madre.sdk.operation.PublicModuleInvoker;
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
        implements ModuleRegistration, PublicModuleInvoker, OwnerModuleInvoker {
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

    /**
     * Creates the read-only directory supplied to one exact installed Module. The caller identity
     * is captured here and is therefore absent from the SDK query object.
     */
    public ModuleDirectory directoryFor(ModuleId caller) {
        ModuleId boundCaller = Objects.requireNonNull(caller, "caller");
        return query -> reachable(boundCaller, query);
    }

    /**
     * Creates the Module receiver invocation port supplied to one exact installed Module. The
     * caller identity is captured by runtime assembly and cannot be supplied by Module code.
     */
    public ModuleInvoker invokerFor(ModuleId caller) {
        ModuleId boundCaller = Objects.requireNonNull(caller, "caller");
        return new ModuleInvoker() {
            @Override public <I, O> CompletionStage<Material<O>> invoke(
                    OperationCall<I, O> call) {
                return invokeModule(boundCaller, call);
            }
        };
    }

    private List<ReachableModule> reachable(ModuleId callerId, ReachabilityQuery query) {
        Objects.requireNonNull(query, "query");
        ModuleDefinition caller = installedCaller(callerId).definition();
        if (!callerCanOffer(caller, query.materialType(), query.sensitivity())) {
            return List.of();
        }
        List<ReachableModule> result = new ArrayList<>();
        entries.values().stream().map(Entry::definition)
                .sorted(Comparator.comparing(module -> module.id().value())).forEach(module -> {
                    Map<OperationId, OperationDefinition> operations = new HashMap<>();
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

    private <I, O> CompletionStage<Material<O>> invokeModule(ModuleId callerId,
            OperationCall<I, O> call) {
        OperationCall<I, O> requested = Objects.requireNonNull(call, "call");
        ModuleDefinition caller = installedCaller(callerId).definition();
        if (!callerCanOffer(caller, requested.input())) {
            throw new IllegalArgumentException(
                    "input Material is not structurally reachable from calling Module " + callerId);
        }
        OperationBinding<?, ?> binding = exactBinding(requested);
        if (binding.definition().visibility() != OperationVisibility.PUBLIC) {
            throw new IllegalArgumentException("Operation is not Module-callable: "
                    + requested.operation().id());
        }
        return invokeModuleExact(binding, requested).thenApply(result -> receive(caller, result));
    }

    private Entry installedCaller(ModuleId callerId) {
        Entry caller = entries.get(Objects.requireNonNull(callerId, "callerId"));
        if (caller == null) {
            throw new IllegalStateException("calling Module is not installed: " + callerId);
        }
        return caller;
    }

    private static boolean callerCanOffer(ModuleDefinition caller,
            io.github.didacll.madre.sdk.identity.MaterialTypeId materialType,
            io.github.didacll.madre.algebra.Sensitivity sensitivity) {
        if (caller.materialTypes().containsKey(materialType)) {
            return materialType.moduleId().equals(caller.id());
        }
        return caller.publicMaterialReferences().contains(materialType)
                && sensitivity.canReach(Privacy.MODULE);
    }

    private static boolean callerCanOffer(ModuleDefinition caller, Material<?> material) {
        if (caller.materialTypes().containsKey(material.type().id())) {
            return material.id().moduleId().equals(caller.id())
                    && material.type().id().moduleId().equals(caller.id());
        }
        return caller.publicMaterialReferences().contains(material.type().id())
                && material.id().moduleId().equals(material.type().id().moduleId())
                && material.sensitivity().canReach(Privacy.MODULE);
    }

    private static <O> Material<O> receive(ModuleDefinition caller, Material<O> result) {
        if (!caller.publicMaterialReferences().contains(result.type().id())) {
            throw new IllegalStateException("calling Module does not declare foreign Material type "
                    + result.type().id());
        }
        if (!result.id().moduleId().equals(result.type().id().moduleId())) {
            throw new IllegalStateException("foreign Material identity does not match its owner");
        }
        if (!result.sensitivity().canReach(Privacy.MODULE)) {
            throw new IllegalStateException("foreign Material Sensitivity cannot reach the "
                    + "Module receiver boundary");
        }
        return result;
    }

    private OperationBinding<?, ?> exactBinding(OperationCall<?, ?> requested) {
        Entry entry = entries.get(requested.operation().id().moduleId());
        if (entry == null) {
            throw new IllegalArgumentException(
                    "Module is not installed: " + requested.operation().id().moduleId());
        }
        OperationBinding<?, ?> binding = entry.instance().operations().get(
                requested.operation().id());
        if (binding == null || !binding.definition().equals(requested.operation())) {
            throw new IllegalArgumentException(
                    "OperationCall contract differs from the installed Operation binding");
        }
        return binding;
    }

    @SuppressWarnings("unchecked")
    private static <I, O> CompletionStage<Material<O>> invokePublicExact(
            OperationBinding<?, ?> binding, OperationCall<I, O> call) {
        return ((OperationBinding<I, O>) binding).invokePublic(call);
    }

    @SuppressWarnings("unchecked")
    private static <I, O> CompletionStage<Material<O>> invokeModuleExact(
            OperationBinding<?, ?> binding, OperationCall<I, O> call) {
        return ((OperationBinding<I, O>) binding).invoke(call);
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
