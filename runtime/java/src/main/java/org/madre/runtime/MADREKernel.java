package org.madre.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MADREKernel {
    private final UUID kernelId;
    private final BoundaryProfile boundary;
    private final RuntimeJournal journal;
    private final Map<UUID, ReasoningModule> modules = new LinkedHashMap<>();
    private final List<ReasoningPlan> scheduledPlans = new ArrayList<>();
    private UUID coreModuleId;

    public MADREKernel(UUID kernelId, BoundaryProfile boundary, RuntimeJournal journal) {
        this.kernelId = Objects.requireNonNull(kernelId, "kernelId");
        this.boundary = Objects.requireNonNull(boundary, "boundary");
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    public static MADREKernel local(BoundaryProfile boundary, RuntimeJournal journal) {
        return new MADREKernel(UUID.randomUUID(), boundary, journal);
    }

    public UUID id() {
        return kernelId;
    }

    public BoundaryProfile boundary() {
        return boundary;
    }

    public RuntimeJournal journal() {
        return journal;
    }

    public List<ReasoningModule> modules() {
        return List.copyOf(modules.values());
    }

    public List<ReasoningPlan> scheduledPlans() {
        return List.copyOf(scheduledPlans);
    }

    public Optional<ReasoningModule> coreModule() {
        return Optional.ofNullable(coreModuleId).map(modules::get);
    }

    public RuntimeEvent registerModule(ReasoningModule module) {
        Objects.requireNonNull(module, "module");
        if (modules.containsKey(module.id())) {
            throw new IllegalArgumentException("module already registered");
        }
        modules.put(module.id(), module);
        return record(RuntimeEvent.now("kernel.module.registered", module.id(), id(), "module registered", null));
    }

    public RuntimeEvent assignCoreModule(UUID moduleId) {
        if (!modules.containsKey(moduleId)) {
            throw new IllegalArgumentException("core module must be registered");
        }
        coreModuleId = moduleId;
        return record(RuntimeEvent.now("kernel.core.assigned", moduleId, kernelId, "core module role assigned", null));
    }

    public RuntimeEvent dispatch(ReasoningRequest request) {
        Objects.requireNonNull(request, "request");
        if (!request.isRoutable()) {
            throw new IllegalArgumentException("request is not routable");
        }
        UUID targetId = request.targetModuleRef().orElseGet(() -> coreModule().orElseThrow(() -> new IllegalStateException("core module is not assigned")).id());
        ReasoningModule target = Optional.ofNullable(modules.get(targetId)).orElseThrow(() -> new IllegalArgumentException("target module is not registered"));
        if (!request.boundary().compatibleWith(target.boundary())) {
            throw new IllegalStateException("request boundary is not compatible with target module");
        }
        Artifact artifact = target.handle(request);
        return record(RuntimeEvent.now("kernel.request.dispatched", request.id(), kernelId, "request dispatched to module", artifact.id()));
    }

    public RuntimeEvent schedule(ReasoningPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (!modules.containsKey(plan.owningModuleId())) {
            throw new IllegalArgumentException("plan owner is not a registered module");
        }
        if (!plan.boundary().compatibleWith(boundary)) {
            throw new IllegalStateException("plan boundary is not compatible with kernel");
        }
        RuntimeEvent scheduled = plan.markScheduled();
        scheduledPlans.add(plan);
        record(scheduled);
        return record(RuntimeEvent.now("kernel.plan.scheduled", plan.id(), id(), "plan scheduled by kernel", null));
    }

    public List<ReasoningModule> moduleViewFor(BoundaryProfile requesterBoundary) {
        Objects.requireNonNull(requesterBoundary, "requesterBoundary");
        return modules.values().stream()
                .filter(module -> module.boundary().compatibleWith(requesterBoundary))
                .toList();
    }

    public RuntimeEvent record(RuntimeEvent event) {
        return journal.append(event);
    }
}
