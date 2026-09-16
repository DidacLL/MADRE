package io.github.didacll.madre.kernel.module;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.directory.ModuleDirectory;
import io.github.didacll.madre.sdk.directory.ReachabilityQuery;
import io.github.didacll.madre.sdk.directory.ReachableAgent;
import io.github.didacll.madre.sdk.directory.ReachableModule;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.material.MaterialTypeDefinition;
import io.github.didacll.madre.sdk.module.AgentDefinition;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.operation.ModuleInvoker;
import io.github.didacll.madre.sdk.operation.OperationCall;
import io.github.didacll.madre.sdk.operation.OwnerInteractionInvoker;
import io.github.didacll.madre.sdk.operation.OwnerModuleInvoker;
import io.github.didacll.madre.sdk.operation.PublicModuleInvoker;
import io.github.didacll.madre.sdk.registration.ModuleRegistration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory registry for currently running executable Modules; it deliberately has no persistence. */
public final class LiveModuleRegistry implements ModuleRegistration, PublicModuleInvoker,
        OwnerModuleInvoker, OwnerInteractionInvoker {
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
     * is captured here and is therefore absent from SDK discovery objects.
     */
    public ModuleDirectory directoryFor(ModuleId caller) {
        ModuleId boundCaller = Objects.requireNonNull(caller, "caller");
        return new ModuleDirectory() {
            @Override public List<ReachableModule> reachable(ReachabilityQuery query) {
                return LiveModuleRegistry.this.reachable(boundCaller, query);
            }

            @Override public List<ReachableModule> exposed(Sensitivity sensitivity) {
                return LiveModuleRegistry.this.exposed(boundCaller, sensitivity);
            }
        };
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
                        if (module.exposedOperations().contains(id) && privacy != null
                                && query.sensitivity().canReach(privacy)) {
                            operations.put(id, operation);
                        }
                    });
                    if (operations.isEmpty()) return;
                    result.add(reachableModule(module, operations));
                });
        return List.copyOf(result);
    }

    private List<ReachableModule> exposed(ModuleId callerId, Sensitivity sensitivity) {
        installedCaller(callerId);
        Sensitivity carried = Objects.requireNonNull(sensitivity, "sensitivity");
        List<ReachableModule> result = new ArrayList<>();
        entries.values().stream().map(Entry::definition)
                .sorted(Comparator.comparing(module -> module.id().value())).forEach(module -> {
                    Map<OperationId, OperationDefinition> operations = new HashMap<>();
                    module.operations().forEach((id, operation) -> {
                        boolean compatibleInput = operation.acceptedMaterial().values().stream()
                                .anyMatch(carried::canReach);
                        if (module.exposedOperations().contains(id) && compatibleInput) {
                            operations.put(id, operation);
                        }
                    });
                    if (operations.isEmpty()) return;
                    result.add(reachableModule(module, operations));
                });
        return List.copyOf(result);
    }

    private static ReachableModule reachableModule(ModuleDefinition module,
            Map<OperationId, OperationDefinition> operations) {
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
        return new ReachableModule(module.id(), module.version(), module.purpose(),
                referencedOwnedMaterialTypes(module, operations), agents, operations);
    }

    private static Map<MaterialTypeId, MaterialTypeDefinition> referencedOwnedMaterialTypes(
            ModuleDefinition module, Map<OperationId, OperationDefinition> operations) {
        Map<MaterialTypeId, MaterialTypeDefinition> result = new LinkedHashMap<>();
        operations.values().stream()
                .sorted(Comparator.comparing(operation -> operation.id().name()))
                .forEach(operation -> {
                    operation.acceptedMaterial().keySet().stream()
                            .sorted(Comparator.comparing(MaterialTypeId::name))
                            .forEach(id -> addOwnedMaterialType(module, result, id));
                    operation.producedMaterial().keySet().stream()
                            .sorted(Comparator.comparing(MaterialTypeId::name))
                            .forEach(id -> addOwnedMaterialType(module, result, id));
                });
        return Map.copyOf(result);
    }

    private static void addOwnedMaterialType(ModuleDefinition module,
            Map<MaterialTypeId, MaterialTypeDefinition> result, MaterialTypeId id) {
        MaterialTypeDefinition definition = module.materialTypes().get(id);
        if (definition != null) result.putIfAbsent(id, definition);
    }

    @Override public <I, O> CompletionStage<Material<O>> invokePublic(OperationCall<I, O> call) {
        OperationCall<I, O> requested = Objects.requireNonNull(call, "call");
        OperationBinding<?, ?> binding = exactBinding(requested);
        return invokePublicExact(binding, requested);
    }

    /** Expert/debug host entry; Module exposure and external/public disclosure are separate. */
    @Override public <I, O> CompletionStage<Material<O>> invokeOwner(OperationCall<I, O> call) {
        OperationCall<I, O> requested = Objects.requireNonNull(call, "call");
        OperationBinding<?, ?> binding = exactBinding(requested);
        return invokeOwnerExact(binding, requested);
    }

    /** Host-only selected interaction entry through the ordinary Module assigned CORE. */
    @Override public <I, O> CompletionStage<Material<O>> invokeOwnerInteraction(
            OperationCall<I, O> call) {
        OperationCall<I, O> requested = Objects.requireNonNull(call, "call");
        ModuleId selectedCore = configuredCore.orElseThrow(() ->
                new IllegalStateException("no Module is assigned the CORE role"));
        if (!selectedCore.equals(requested.operation().id().moduleId())
                || !entries.containsKey(selectedCore)) {
            throw new IllegalArgumentException(
                    "owner interaction must target the installed Module assigned CORE");
        }
        OperationBinding<?, ?> binding = exactBinding(requested);
        if (!binding.ownerInteractionEntryPoint()) {
            throw new IllegalArgumentException("Operation is not an owner-interaction entry point: "
                    + requested.operation().id());
        }
        return invokeOwnerExact(binding, requested);
    }

    private <I, O> CompletionStage<Material<O>> invokeModule(ModuleId callerId,
            OperationCall<I, O> call) {
        OperationCall<I, O> requested = Objects.requireNonNull(call, "call");
        ModuleDefinition caller = installedCaller(callerId).definition();
        Entry callee = installedCallee(requested.operation().id().moduleId());
        if (!callerCanOffer(caller, callee, requested.input())) {
            throw new IllegalArgumentException(
                    "input Material is not structurally reachable from calling Module " + callerId);
        }
        OperationBinding<?, ?> binding = exactBinding(requested);
        if (!callee.definition().exposedOperations().contains(requested.operation().id())) {
            throw new IllegalArgumentException("Operation is not exposed by its Module: "
                    + requested.operation().id());
        }
        return invokeModuleExact(binding, requested)
                .thenApply(result -> receive(caller, callee.definition(), result));
    }

    private Entry installedCaller(ModuleId callerId) {
        Entry caller = entries.get(Objects.requireNonNull(callerId, "callerId"));
        if (caller == null) {
            throw new IllegalStateException("calling Module is not installed: " + callerId);
        }
        return caller;
    }

    private Entry installedCallee(ModuleId calleeId) {
        Entry callee = entries.get(Objects.requireNonNull(calleeId, "calleeId"));
        if (callee == null) {
            throw new IllegalArgumentException("Module is not installed: " + calleeId);
        }
        return callee;
    }

    private static boolean callerCanOffer(ModuleDefinition caller,
            MaterialTypeId materialType, Sensitivity sensitivity) {
        if (caller.materialTypes().containsKey(materialType)) {
            return materialType.moduleId().equals(caller.id());
        }
        return caller.foreignMaterialReferences().contains(materialType)
                && sensitivity.canReach(Privacy.MODULE);
    }

    private static boolean callerCanOffer(ModuleDefinition caller, Entry callee,
            Material<?> material) {
        if (!material.id().moduleId().equals(caller.id())) {
            return false;
        }
        MaterialTypeDefinition local = caller.materialTypes().get(material.type().id());
        if (local != null) {
            return local.equals(material.type().definition());
        }
        MaterialTypeDefinition receiverPublished = callee.definition().materialTypes()
                .get(material.type().id());
        if (receiverPublished != null) {
            MaterialType<?> receiverBinding = callee.instance().materialTypes().get(material.type().id());
            return receiverPublished.equals(material.type().definition())
                    && receiverBinding != null
                    && receiverBinding.javaType().isInstance(material.payload());
        }
        return caller.foreignMaterialReferences().contains(material.type().id())
                && material.sensitivity().canReach(Privacy.MODULE);
    }

    private static <O> Material<O> receive(ModuleDefinition caller, ModuleDefinition callee,
            Material<O> result) {
        MaterialTypeDefinition local = caller.materialTypes().get(result.type().id());
        if (local != null) {
            if (!local.equals(result.type().definition())) {
                throw new IllegalStateException(
                        "received Material does not match the caller-owned nominal contract");
            }
        } else {
            MaterialTypeDefinition producerPublished = callee.materialTypes().get(result.type().id());
            if (producerPublished != null) {
                if (!producerPublished.equals(result.type().definition())) {
                    throw new IllegalStateException(
                            "received Material does not match the producer-owned nominal contract");
                }
            } else if (!caller.foreignMaterialReferences().contains(result.type().id())) {
                throw new IllegalStateException("calling Module does not declare Material type "
                        + result.type().id());
            }
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
