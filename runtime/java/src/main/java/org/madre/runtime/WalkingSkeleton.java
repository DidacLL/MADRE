package org.madre.runtime;

public final class WalkingSkeleton {
    private WalkingSkeleton() {
    }

    public static RuntimeJournal run() {
        RuntimeJournal journal = new RuntimeJournal();
        BoundaryProfile boundary = BoundaryProfile.internalProfile();
        MADREKernel kernel = MADREKernel.local(boundary, journal);
        ReasoningModule module = ReasoningModule.core("core", boundary, journal);
        MADREAgent agent = MADREAgent.simple("simple-agent", boundary);
        MADREAction action = MADREAction.deterministic(agent.id(), "echo-artifact", boundary, request -> Artifact.response("handled: " + request.objective(), request.boundary()));
        agent.addAction(action);
        module.registerAgent(agent);
        module.registerAction(action);
        module.registerWorkflow(Workflow.singleTask("single-step", boundary));

        kernel.registerModule(module);
        kernel.assignCoreModule(module.id());

        ReasoningRequest request = ReasoningRequest.coreRequest("produce first artifact", kernel, boundary);
        kernel.dispatch(request);
        ReasoningPlan plan = module.createPlan(request);
        kernel.schedule(plan);
        module.advance(plan);

        return journal;
    }

    public static void main(String[] args) {
        RuntimeJournal journal = run();
        journal.events().forEach(event -> System.out.printf("%s %s%n", event.type(), event.summary()));
    }
}
