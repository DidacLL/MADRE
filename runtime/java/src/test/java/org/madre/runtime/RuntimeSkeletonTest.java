package org.madre.runtime;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeSkeletonTest {
    @Test
    void coreRoutingDispatchesToCoreModuleWhenNoTargetIsSet() {
        Fixture fixture = Fixture.create();
        ReasoningRequest request = ReasoningRequest.coreRequest("answer directly", fixture.kernel(), fixture.boundary());

        RuntimeEvent event = fixture.kernel().dispatch(request);

        assertEquals("kernel.request.dispatched", event.type());
        assertEquals(request.id(), event.subjectId());
        assertEquals(0, fixture.kernel().scheduledPlans().size());
        assertTrue(fixture.journal().trace(request.id()).stream().anyMatch(trace -> trace.type().equals("module.request.handled")));
    }

    @Test
    void kernelSchedulesPlansNotRawRequests() {
        Fixture fixture = Fixture.create();
        ReasoningRequest request = ReasoningRequest.coreRequest("make a plan", fixture.kernel(), fixture.boundary());
        ReasoningPlan plan = fixture.module().createPlan(request);

        RuntimeEvent event = fixture.kernel().schedule(plan);

        assertEquals("kernel.plan.scheduled", event.type());
        assertEquals(Status.SCHEDULED, plan.status());
        assertEquals(1, fixture.kernel().scheduledPlans().size());
        assertThrows(NoSuchMethodException.class, () -> MADREKernel.class.getMethod("schedule", ReasoningRequest.class));
    }

    @Test
    void reasoningTaskIsPassiveAndAdvancedThroughModulePlanPath() {
        Fixture fixture = Fixture.create();
        ReasoningPlan plan = fixture.module().createPlan(ReasoningRequest.coreRequest("advance one task", fixture.kernel(), fixture.boundary()));
        fixture.kernel().schedule(plan);
        ReasoningTask task = plan.tasks().getFirst();

        Set<String> methodNames = Arrays.stream(ReasoningTask.class.getMethods()).map(Method::getName).collect(java.util.stream.Collectors.toSet());

        assertFalse(methodNames.contains("execute"));
        assertFalse(methodNames.contains("run"));
        fixture.module().advance(plan);
        assertEquals(Status.COMPLETED, task.status());
    }

    @Test
    void workflowApplicabilityDoesNotCreateTasksOrExecuteActions() {
        Fixture fixture = Fixture.create();
        Workflow workflow = Workflow.singleTask("blueprint only", fixture.boundary());
        ReasoningRequest request = ReasoningRequest.coreRequest("check workflow", fixture.kernel(), fixture.boundary());

        boolean applicable = workflow.applicableTo(request);

        assertTrue(applicable);
        assertEquals(1, workflow.taskTemplates().size());
        assertTrue(fixture.journal().events().stream().noneMatch(event -> event.type().contains("action")));
    }

    @Test
    void boundaryJoinReturnsConservativeProfile() {
        BoundaryProfile open = BoundaryProfile.publicProfile();
        BoundaryProfile restricted = BoundaryProfile.restrictedProfile();

        BoundaryProfile joined = open.join(restricted);

        assertEquals(BoundaryValue.RESTRICTED, joined.scope());
        assertEquals(BoundaryValue.RESTRICTED, joined.risk());
        assertTrue(joined.validationRequired());
    }

    @Test
    void policyDecisionCanBlockRiskyActionPath() {
        BoundaryProfile boundary = BoundaryProfile.internalProfile();
        MADREAgent agent = MADREAgent.simple("policy-agent", boundary);
        MADREAction action = MADREAction.deterministic(agent.id(), "must-not-run", boundary, request -> {
            throw new AssertionError("blocked action should not execute");
        });
        agent.addAction(action);
        AgentRequest request = new AgentRequest(
                UUID.randomUUID(),
                "blocked request",
                UUID.randomUUID(),
                agent.id(),
                action.id(),
                null,
                Map.of(),
                boundary,
                PolicyDecision.block(action.id(), boundary, "negative path"),
                java.time.Instant.now()
        );

        Artifact artifact = agent.execute(request);

        assertEquals(ArtifactKind.FAILURE, artifact.kind());
        assertTrue(artifact.contentRef().contains("blocked by policy"));
    }

    @Test
    void artifactDoesNotPromoteToKnowledgeRecordByDefault() {
        Artifact artifact = Artifact.generated("generated material", BoundaryProfile.restrictedProfile());

        Object material = artifact;

        assertTrue(artifact.requiresValidation());
        assertFalse(material instanceof KnowledgeRecord);
    }

    @Test
    void runtimeJournalPreservesAppendOrderAndTraceLookup() {
        RuntimeJournal journal = new RuntimeJournal();
        UUID subject = UUID.randomUUID();
        RuntimeEvent first = RuntimeEvent.now("first", subject, subject, "first event", null);
        RuntimeEvent second = RuntimeEvent.now("second", subject, subject, "second event", null);

        journal.append(first);
        journal.append(second);

        assertEquals(first.id(), journal.events().get(0).id());
        assertEquals(second.id(), journal.events().get(1).id());
        assertEquals(2, journal.trace(subject).size());
        assertThrows(IllegalArgumentException.class, () -> journal.append(first));
    }

    @Test
    void walkingSkeletonProducesJournaledRuntimeFlow() {
        RuntimeJournal journal = WalkingSkeleton.run();

        assertTrue(journal.events().stream().anyMatch(event -> event.type().equals("kernel.module.registered")));
        assertTrue(journal.events().stream().anyMatch(event -> event.type().equals("kernel.request.dispatched")));
        assertTrue(journal.events().stream().anyMatch(event -> event.type().equals("kernel.plan.scheduled")));
        assertTrue(journal.events().stream().anyMatch(event -> event.type().equals("module.plan.advanced")));
    }

    @Test
    void noDeferredArchitectureConceptsAreIntroducedAsDomainClasses() throws Exception {
        Path domainRoot = Path.of("src/main/java/org/madre/runtime");
        Set<String> deferred = Set.of(
                "SystemAgent",
                "SimpleAgent",
                "TwinAgent",
                "ComplexAgent",
                "AgentResponse",
                "ModelAction",
                "AgentRoutine",
                "InferenceProfile",
                "InferenceBackend",
                "InferenceRecord",
                "InferenceOutput",
                "KnowledgeCandidate",
                "LearningCandidate",
                "ConversationRecord",
                "ActionRegistry",
                "Scheduler",
                "ContextSource"
        );

        try (var files = Files.list(domainRoot)) {
            Set<String> javaClassNames = files
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString().replace(".java", ""))
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(java.util.Collections.disjoint(javaClassNames, deferred));
        }
    }

    private record Fixture(MADREKernel kernel, ReasoningModule module, RuntimeJournal journal, BoundaryProfile boundary) {
        static Fixture create() {
            RuntimeJournal journal = new RuntimeJournal();
            BoundaryProfile boundary = BoundaryProfile.internalProfile();
            MADREKernel kernel = MADREKernel.local(boundary, journal);
            ReasoningModule module = ReasoningModule.core("core", boundary, journal);
            MADREAgent agent = MADREAgent.simple("agent", boundary);
            MADREAction action = MADREAction.deterministic(agent.id(), "echo", boundary, request -> Artifact.response("ok: " + request.objective(), request.boundary()));
            agent.addAction(action);
            module.registerAgent(agent);
            module.registerAction(action);
            module.registerWorkflow(Workflow.singleTask("single", boundary));
            kernel.registerModule(module);
            kernel.assignCoreModule(module.id());
            return new Fixture(kernel, module, journal, boundary);
        }
    }
}
