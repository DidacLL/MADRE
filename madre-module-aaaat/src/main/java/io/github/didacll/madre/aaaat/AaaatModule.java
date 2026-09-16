package io.github.didacll.madre.aaaat;

import io.github.didacll.madre.algebra.Autonomy;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Risk;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.EffectProfileId;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialCodecs;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.EffectProfile;
import io.github.didacll.madre.sdk.module.Module;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
import io.github.didacll.madre.sdk.operation.Operation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Ordinary MADRE Module adapting AAAAT's official bounded stdio interface. */
final class AaaatModule implements Module {
    private static final String JSON_MEDIA_TYPE = "application/json; charset=utf-8";

    private static final MaterialType<String> OPPORTUNITY_REQUEST = type(
            AaaatContracts.OPPORTUNITY_RESEARCH_REQUEST, "Bounded request for selected opportunity research context");
    private static final MaterialType<String> OPPORTUNITY_CONTEXT = type(
            AaaatContracts.OPPORTUNITY_RESEARCH_CONTEXT, "AAAAT-selected opportunity research context JSON");
    private static final MaterialType<String> SOURCE_ADD_REQUEST = type(
            AaaatContracts.CANDIDATURE_SOURCE_ADD_REQUEST, "Bounded AAAAT candidature source-add request JSON");
    private static final MaterialType<String> SOURCE_ADD_RESULT = type(
            AaaatContracts.CANDIDATURE_SOURCE_ADD_RESULT, "AAAAT candidature source-add acknowledgement JSON");
    private static final MaterialType<String> CAREER_REQUEST = type(
            AaaatContracts.CAREER_CONTEXT_REQUEST, "Private career-context request JSON");
    private static final MaterialType<String> CAREER_CONTEXT = type(
            AaaatContracts.CAREER_CONTEXT, "Private AAAAT career context JSON");

    private final AaaatMcpClient client;
    private final OperationBinding<String, String> opportunityRead;
    private final OperationBinding<String, String> sourceAdd;
    private final OperationBinding<String, String> careerRead;

    AaaatModule(AaaatMcpClient client) {
        this.client = java.util.Objects.requireNonNull(client, "client");

        OperationDefinition opportunityContract = new OperationDefinition(
                AaaatContracts.OPPORTUNITY_RESEARCH_READ,
                "Read the currently selected AAAAT opportunity's bounded research context",
                OperationVisibility.PUBLIC,
                Map.of(OPPORTUNITY_REQUEST.id(), Privacy.MODULE),
                Map.of(OPPORTUNITY_CONTEXT.id(), Sensitivity.S4),
                Map.of());
        opportunityRead = OperationBinding.publicOperation(opportunityContract,
                tool("opportunity_research_context_read", OPPORTUNITY_CONTEXT, Sensitivity.S4),
                AaaatModule::publicReadSummary);

        EffectProfile write = new EffectProfile(
                new EffectProfileId(AaaatContracts.CANDIDATURE_SOURCE_ADD, "add-source"),
                Risk.WRITE, Autonomy.ASK_ALWAYS);
        OperationDefinition sourceContract = new OperationDefinition(
                AaaatContracts.CANDIDATURE_SOURCE_ADD,
                "Add one bounded source to AAAAT's currently selected candidature",
                OperationVisibility.PUBLIC,
                Map.of(SOURCE_ADD_REQUEST.id(), Privacy.MODULE),
                Map.of(SOURCE_ADD_RESULT.id(), Sensitivity.S4),
                Map.of(write.id(), write));
        sourceAdd = OperationBinding.publicOperation(sourceContract,
                tool("candidature_source_add", SOURCE_ADD_RESULT, Sensitivity.S4),
                AaaatModule::publicWriteSummary);

        OperationDefinition careerContract = new OperationDefinition(
                AaaatContracts.CAREER_CONTEXT_READ,
                "Internal AAAAT career context read not exposed for Module composition",
                OperationVisibility.PRIVATE,
                Map.of(CAREER_REQUEST.id(), Privacy.MODULE),
                Map.of(CAREER_CONTEXT.id(), Sensitivity.S4),
                Map.of());
        careerRead = OperationBinding.privateOperation(careerContract,
                tool("career_context_read", CAREER_CONTEXT, Sensitivity.S4));
    }

    @Override public io.github.didacll.madre.sdk.identity.ModuleId id() {
        return AaaatContracts.MODULE_ID;
    }

    @Override public String version() { return "0.1.0"; }

    @Override public String purpose() {
        return "Bounded integration with an owner-installed AAAAT career-application workspace";
    }

    @Override public Collection<? extends MaterialType<?>> materialTypes() {
        return List.of(OPPORTUNITY_REQUEST, OPPORTUNITY_CONTEXT, SOURCE_ADD_REQUEST,
                SOURCE_ADD_RESULT, CAREER_REQUEST, CAREER_CONTEXT);
    }

    @Override public Collection<? extends OperationBinding<?, ?>> operations() {
        return List.of(opportunityRead, sourceAdd, careerRead);
    }

    private Operation<String, String> tool(String toolName, MaterialType<String> output,
            Sensitivity sensitivity) {
        return Operation.of(call -> CompletableFuture.supplyAsync(() -> new Material<>(
                new MaterialId(AaaatContracts.MODULE_ID,
                        call.operation().id().name() + "-" + UUID.randomUUID()),
                output, client.call(toolName, call.input().payload()), sensitivity)));
    }

    private static Material<String> publicReadSummary(Material<String> internal) {
        return new Material<>(new MaterialId(AaaatContracts.MODULE_ID,
                "public-read-" + UUID.randomUUID()), internal.type(),
                "{\"available\":true}", Sensitivity.S1);
    }

    private static Material<String> publicWriteSummary(Material<String> internal) {
        return new Material<>(new MaterialId(AaaatContracts.MODULE_ID,
                "public-write-" + UUID.randomUUID()), internal.type(),
                "{\"completed\":true}", Sensitivity.S1);
    }

    private static MaterialType<String> type(
            io.github.didacll.madre.sdk.identity.MaterialTypeId id, String schema) {
        return new MaterialType<>(id, String.class, JSON_MEDIA_TYPE + "; schema=" + schema,
                MaterialCodecs.utf8String());
    }
}
