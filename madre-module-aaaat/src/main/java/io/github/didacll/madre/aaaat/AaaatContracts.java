package io.github.didacll.madre.aaaat;

import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;

/** Stable nominal identities published by the independently installable AAAAT Module. */
public final class AaaatContracts {
    public static final ModuleId MODULE_ID = new ModuleId("io.github.didacll.madre.aaaat");

    public static final MaterialTypeId OPPORTUNITY_RESEARCH_REQUEST =
            new MaterialTypeId(MODULE_ID, "opportunity-research-request");
    public static final MaterialTypeId OPPORTUNITY_RESEARCH_CONTEXT =
            new MaterialTypeId(MODULE_ID, "opportunity-research-context");
    public static final MaterialTypeId CANDIDATURE_SOURCE_ADD_REQUEST =
            new MaterialTypeId(MODULE_ID, "candidature-source-add-request");
    public static final MaterialTypeId CANDIDATURE_SOURCE_ADD_RESULT =
            new MaterialTypeId(MODULE_ID, "candidature-source-add-result");
    public static final MaterialTypeId CAREER_CONTEXT_REQUEST =
            new MaterialTypeId(MODULE_ID, "career-context-request");
    public static final MaterialTypeId CAREER_CONTEXT =
            new MaterialTypeId(MODULE_ID, "career-context");

    public static final OperationId OPPORTUNITY_RESEARCH_READ =
            new OperationId(MODULE_ID, "opportunity-research-context-read");
    public static final OperationId CANDIDATURE_SOURCE_ADD =
            new OperationId(MODULE_ID, "candidature-source-add");
    public static final OperationId CAREER_CONTEXT_READ =
            new OperationId(MODULE_ID, "career-context-read");

    private AaaatContracts() {}
}
