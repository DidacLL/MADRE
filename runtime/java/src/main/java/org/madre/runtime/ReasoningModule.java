package org.madre.runtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ReasoningModule {
    private final UUID moduleId;
    private final String name;
    private final String purpose;
    private final BoundaryProfile boundary;
    private final RuntimeJournal journal;
    private final List<MADREAgent> agents = new ArrayList<>();
    private final List<MADREAction> actions = new ArrayList<>();
    private final List<Workflow> workflows = new ArrayList<>();
    private final List<KnowledgeRecord> knowledgeRecords = new ArrayList<>();
    private final List<ReasoningPlan> plans = new ArrayList<>();

    public ReasoningModule(UUID moduleId, String name, String purpose, BoundaryProfile boundary, RuntimeJournal journal) {
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        this.name = Objects.requireNonNull(name, "name");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.boundary = Objects.requireNonNull(boundary, "boundary");
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    public static ReasoningModule core(String name, BoundaryProfile boundary, RuntimeJournal journal) {
        return new ReasoningModule(UUID.randomUUID(), name, "core walking skeleton module", boundary, journal);
    }

    public UUID id() {
        return moduleId;
    }

    public String name() {
        return name;
    }

    public String purpose() {
        return purpose;
    }

    public BoundaryProfile boundary() {
        return boundary;
    }

    public List<MADREAgent> agents() {
        return List.copyOf(agents);
    }

    public List<MADREAction> actions() {
        return List.copyOf(actions);
    }

    public List<Workflow> workflows() {
        return List.copyOf(workflows);
    }

    public List<KnowledgeRecord> knowledgeRecords() {
        return List.copyOf(knowledgeRecords);
    }

    public List<ReasoningPlan> plans() {
        return List.copyOf(plans);
    }

    public void registerAgent(MADREAgent agent) {
        agents.add(Objects.requireNonNull(agent, "agent"));
    }

    public void registerAction(MADREAction action) {
        actions.add(Objects.requireNonNull(action, "action"));
    }

    public void registerWorkflow(Workflow workflow) {
        workflows.add(Objects.requireNonNull(workflow, "workflow"));
    }

    public Artifact handle(ReasoningRequest request) {
        Objects.requireNonNull(request, "request");
        if (!request.boundary().compatibleWith(boundary)) {
            Artifact blocked = Artifact.failure("request boundary is not compatible with module", request.boundary());
            journal.append(RuntimeEvent.now("module.request.blocked", request.id(), moduleId, "module rejected request boundary", blocked.id()));
            return blocked;
        }
        if (agents.isEmpty()) {
            Artifact failure = Artifact.failure("module has no agent", boundary);
            journal.append(RuntimeEvent.now("module.request.failed", request.id(), moduleId, "module has no agent", failure.id()));
            return failure;
        }
        MADREAgent agent = agents.getFirst();
        MADREAction action = agent.actions().isEmpty() ? null : agent.actions().getFirst();
        if (action == null) {
            Artifact failure = Artifact.failure("module agent has no action", boundary);
            journal.append(RuntimeEvent.now("module.request.failed", request.id(), moduleId, "agent has no action", failure.id()));
            return failure;
        }
        AgentRequest agentRequest = new AgentRequest(
                UUID.randomUUID(),
                request.objective(),
                moduleId,
                agent.id(),
                action.id(),
                null,
                Map.of("expectedResult", request.expectedResult()),
                request.boundary(),
                PolicyDecision.allow(action.id(), action.boundary(), "walking skeleton allow"),
                Instant.now()
        );
        Artifact artifact = agent.execute(agentRequest);
        journal.append(RuntimeEvent.now("module.request.handled", request.id(), moduleId, "module handled request", artifact.id()));
        return artifact;
    }

    public ReasoningPlan createPlan(ReasoningRequest request) {
        Objects.requireNonNull(request, "request");
        if (agents.isEmpty() || agents.getFirst().actions().isEmpty()) {
            throw new IllegalStateException("module needs one agent with one action to create a walking skeleton plan");
        }
        ReasoningPlan plan = new ReasoningPlan(UUID.randomUUID(), request.id(), moduleId, request.boundary());
        Workflow workflow = workflows.stream()
                .filter(candidate -> candidate.applicableTo(request))
                .findFirst()
                .orElseGet(() -> Workflow.singleTask("default walking workflow", request.boundary()));
        if (!workflows.contains(workflow)) {
            workflows.add(workflow);
        }
        plan.addWorkflow(workflow);
        MADREAgent agent = agents.getFirst();
        MADREAction action = agent.actions().getFirst();
        AgentRequest agentRequest = new AgentRequest(
                UUID.randomUUID(),
                request.objective(),
                plan.id(),
                agent.id(),
                action.id(),
                null,
                Map.of(),
                request.boundary(),
                PolicyDecision.allow(action.id(), action.boundary(), "plan task allowed"),
                Instant.now()
        );
        ReasoningTask task = new ReasoningTask(
                UUID.randomUUID(),
                plan.id(),
                moduleId,
                agent.id(),
                agentRequest.id(),
                request.objective(),
                request.expectedResult(),
                List.of(),
                true,
                Status.READY
        );
        plan.addTask(task);
        plans.add(plan);
        journal.append(RuntimeEvent.now("module.plan.created", request.id(), moduleId, "module created plan", plan.id()));
        return plan;
    }

    public RuntimeEvent advance(ReasoningPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (!moduleId.equals(plan.owningModuleId())) {
            throw new IllegalArgumentException("module cannot advance a plan it does not own");
        }
        if (plan.readyTasks().isEmpty()) {
            RuntimeEvent event = RuntimeEvent.now("module.plan.idle", plan.id(), id(), "no ready task to advance", null);
            return journal.append(event);
        }
        ReasoningTask task = plan.readyTasks().getFirst();
        MADREAgent agent = agents.stream().filter(candidate -> candidate.id().equals(task.agentId())).findFirst().orElseThrow();
        MADREAction action = agent.actions().stream().findFirst().orElseThrow();
        AgentRequest agentRequest = new AgentRequest(
                task.agentRequestId(),
                task.objective(),
                plan.id(),
                agent.id(),
                action.id(),
                null,
                Map.of(),
                plan.boundary(),
                PolicyDecision.allow(action.id(), action.boundary(), "advance allowed"),
                Instant.now()
        );
        Artifact artifact = agent.execute(agentRequest);
        journal.append(plan.recordTaskResult(task.id(), artifact.id()));
        RuntimeEvent event = RuntimeEvent.now("module.plan.advanced", plan.id(), moduleId, "module advanced one ready task", artifact.id());
        return journal.append(event);
    }

    public ReasoningRequest createDelegatedRequest(ReasoningPlan plan, ReasoningTask task, UUID targetModuleId) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(targetModuleId, "targetModuleId");
        if (!moduleId.equals(plan.owningModuleId())) {
            throw new IllegalArgumentException("plan is not owned by module");
        }
        ReasoningRequest delegated = new ReasoningRequest(
                UUID.randomUUID(),
                task.objective(),
                task.id(),
                targetModuleId,
                List.of(),
                plan.boundary(),
                task.expectedResult(),
                List.of(plan.sourceRequestId()),
                Instant.now()
        );
        journal.append(plan.linkDelegatedRequest(task.id(), delegated.id()));
        return delegated;
    }

    public ContextBundle buildContext(ReasoningRequest request) {
        Objects.requireNonNull(request, "request");
        BoundaryProfile contextBoundary = request.boundary().join(boundary);
        return new ContextBundle(UUID.randomUUID(), request.id(), request.contextIds(), request.objective(), contextBoundary, Instant.now());
    }
}
