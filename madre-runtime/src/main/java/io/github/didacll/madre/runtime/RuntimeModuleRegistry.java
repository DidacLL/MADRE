package io.github.didacll.madre.runtime;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.directory.ModuleDirectory;
import io.github.didacll.madre.sdk.directory.ReachabilityQuery;
import io.github.didacll.madre.sdk.directory.ReachableAgent;
import io.github.didacll.madre.sdk.directory.ReachableModule;
import io.github.didacll.madre.sdk.directory.ReachableOperation;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.module.Agent;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Live semantic registry. Kernel never sees this object or any value stored in it. */
public final class RuntimeModuleRegistry {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<ModuleId, ModuleInstance> modules = new LinkedHashMap<>();
    private ModuleId coreModule;
    private AgentId defaultCoreAgent;

    public void install(ModuleInstance module) {
        ModuleInstance value = Objects.requireNonNull(module, "module");
        lock.writeLock().lock();
        try {
            ModuleId id = value.definition().id();
            if (modules.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException("Module is already installed: " + id);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Owner-authorized direct replacement; no shipped/manual artifact receives immunity. */
    public Optional<ModuleInstance> replace(ModuleInstance module) {
        ModuleInstance value = Objects.requireNonNull(module, "module");
        lock.writeLock().lock();
        try {
            return Optional.ofNullable(modules.put(value.definition().id(), value));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Owner-authorized direct removal, including CORE after making the consequence explicit. */
    public Optional<ModuleInstance> remove(ModuleId id) {
        Objects.requireNonNull(id, "id");
        lock.writeLock().lock();
        try {
            if (id.equals(coreModule)) {
                coreModule = null;
                defaultCoreAgent = null;
            }
            return Optional.ofNullable(modules.remove(id));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void assignCore(ModuleId id, AgentId defaultAgent) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(defaultAgent, "defaultAgent");
        lock.writeLock().lock();
        try {
            ModuleInstance selected = modules.get(id);
            if (selected == null) throw new IllegalArgumentException("CORE Module is not installed: " + id);
            if (!defaultAgent.moduleId().equals(id)
                    || !selected.agents().containsKey(defaultAgent)) {
                throw new IllegalArgumentException("Default CORE Agent is not provided by Module: "
                        + defaultAgent);
            }
            coreModule = id;
            defaultCoreAgent = defaultAgent;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<ModuleId> coreModule() {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(coreModule);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<AgentId> defaultCoreAgentId() {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(defaultCoreAgent);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<ModuleInstance> modules() {
        lock.readLock().lock();
        try {
            return List.copyOf(modules.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public ModuleInstance requireModule(ModuleId id) {
        lock.readLock().lock();
        try {
            ModuleInstance module = modules.get(Objects.requireNonNull(id, "id"));
            if (module == null) throw new IllegalArgumentException("Module is not installed: " + id);
            return module;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Agent resolveAgent(OperationId operation, Optional<AgentId> explicitAgent) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(explicitAgent, "explicitAgent");
        lock.readLock().lock();
        try {
            requireBinding(operation);
            if (explicitAgent.isPresent()) return requireAgent(explicitAgent.orElseThrow());

            ModuleInstance owner = modules.get(operation.moduleId());
            List<Agent> claiming = owner.agents().values().stream()
                    .filter(agent -> agent.operations().contains(operation)).toList();
            if (claiming.size() == 1) return claiming.getFirst();
            if (claiming.size() > 1) {
                throw new AgentResolutionException(
                        "Several Module Agents claim Operation " + operation
                                + "; select the acting Agent explicitly");
            }
            return defaultCoreAgent();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Agent requireAgent(AgentId id) {
        ModuleInstance module = modules.get(Objects.requireNonNull(id, "id").moduleId());
        if (module == null) throw new AgentResolutionException("Agent Module is not installed: " + id);
        Agent agent = module.agents().get(id);
        if (agent == null) throw new AgentResolutionException("Agent is not installed: " + id);
        return agent;
    }

    public OperationBinding<?, ?> requireBinding(OperationId id) {
        ModuleInstance module = modules.get(Objects.requireNonNull(id, "id").moduleId());
        if (module == null) throw new IllegalArgumentException("Operation Module is not installed: " + id);
        OperationBinding<?, ?> operation = module.operations().get(id);
        if (operation == null) throw new IllegalArgumentException("Operation is not installed: " + id);
        return operation;
    }

    public ModuleDirectory directoryFor(ModuleId caller) {
        requireModule(Objects.requireNonNull(caller, "caller"));
        return new LiveDirectory(caller);
    }

    private Agent defaultCoreAgent() {
        if (coreModule == null || defaultCoreAgent == null) {
            throw new AgentResolutionException("No explicit, owning, or default CORE Agent is available");
        }
        return requireAgent(defaultCoreAgent);
    }

    private final class LiveDirectory implements ModuleDirectory {
        private final ModuleId caller;

        private LiveDirectory(ModuleId caller) { this.caller = caller; }

        @Override
        public List<ReachableModule> reachable(ReachabilityQuery query) {
            Objects.requireNonNull(query, "query");
            lock.readLock().lock();
            try {
                List<ReachableModule> result = new ArrayList<>();
                modules.values().stream()
                        .filter(module -> !module.definition().id().equals(caller))
                        .forEach(module -> {
                            Map<OperationId, io.github.didacll.madre.sdk.module.OperationDefinition> operations =
                                    new LinkedHashMap<>();
                            module.definition().exposedOperations().stream()
                                    .map(module.definition().operations()::get)
                                    .filter(operation -> operation.acceptedMaterial().entrySet().stream()
                                            .anyMatch(entry -> entry.getKey().equals(query.materialType())
                                                    && query.sensitivity().canReach(entry.getValue())))
                                    .forEach(operation -> operations.put(operation.id(), operation));
                            if (!operations.isEmpty()) {
                                Map<AgentId, ReachableAgent> agents = new LinkedHashMap<>();
                                module.agents().values().forEach(agent -> {
                                    var visible = agent.operations().stream()
                                            .filter(operations::containsKey)
                                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
                                    if (!visible.isEmpty()) {
                                        agents.put(agent.id(), new ReachableAgent(agent.id(), agent.purpose(),
                                                visible, Privacy.PUBLIC));
                                    }
                                });
                                result.add(new ReachableModule(module.definition().id(),
                                        module.definition().version(), module.definition().purpose(), agents,
                                        operations));
                            }
                        });
                return List.copyOf(result);
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public List<ReachableOperation> reachableOperations(Sensitivity sensitivity) {
            Objects.requireNonNull(sensitivity, "sensitivity");
            lock.readLock().lock();
            try {
                List<ReachableOperation> result = new ArrayList<>();
                modules.values().stream()
                        .filter(module -> !module.definition().id().equals(caller))
                        .forEach(module -> module.definition().exposedOperations().stream()
                                .map(module.definition().operations()::get)
                                .filter(operation -> operation.acceptedMaterial().values().stream()
                                        .anyMatch(sensitivity::canReach))
                                .forEach(operation -> result.add(new ReachableOperation(
                                        module.definition().id(), module.definition().purpose(), operation,
                                        module.definition().materialTypes().entrySet().stream()
                                                .filter(entry -> operation.acceptedMaterial().containsKey(entry.getKey())
                                                        || operation.producedMaterial().containsKey(entry.getKey()))
                                                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                                                        Map.Entry::getKey, Map.Entry::getValue))))));
                return List.copyOf(result);
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    @SuppressWarnings("unchecked")
    <I, O> OperationBinding<I, O> typedBinding(OperationCall<I, O> call) {
        OperationBinding<?, ?> binding = requireBinding(call.operation().id());
        if (!binding.definition().equals(call.operation())) {
            throw new IllegalArgumentException("OperationCall differs from the installed Operation");
        }
        return (OperationBinding<I, O>) binding;
    }
}
