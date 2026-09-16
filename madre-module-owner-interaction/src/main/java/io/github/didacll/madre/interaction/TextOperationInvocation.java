package io.github.didacll.madre.interaction;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialCodecs;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.operation.ModuleInvoker;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Constructs and invokes one exact discovered textual Operation through the ordinary Module port. */
final class TextOperationInvocation {
    private final ModuleId callerId;
    private final ModuleInvoker invoker;

    TextOperationInvocation(ModuleId callerId, ModuleInvoker invoker) {
        this.callerId = Objects.requireNonNull(callerId, "callerId");
        this.invoker = Objects.requireNonNull(invoker, "invoker");
    }

    CompletionStage<Material<String>> invoke(TextOperationDiscovery.Candidate candidate,
            String ownerText, Sensitivity sensitivity, Integrity causalIntegrity) {
        Objects.requireNonNull(candidate, "candidate");
        String text = Objects.requireNonNull(ownerText, "ownerText").strip();
        if (text.isEmpty()) throw new IllegalArgumentException("ownerText must not be blank");
        Objects.requireNonNull(sensitivity, "sensitivity");
        Objects.requireNonNull(causalIntegrity, "causalIntegrity");

        MaterialType<String> targetInput = new MaterialType<>(candidate.inputType(), String.class,
                MaterialCodecs.utf8String());
        Material<String> input = new Material<>(
                new MaterialId(callerId, UUID.randomUUID().toString()), targetInput, text,
                sensitivity);
        OperationCall<String, String> call;
        if (candidate.effectProfile().isPresent()) {
            call = OperationCall.withEffect(candidate.reachable().operation(),
                    candidate.effectProfile().orElseThrow(), input, List.of(causalIntegrity));
        } else {
            call = OperationCall.withoutEffect(candidate.reachable().operation(), input);
        }

        return invoker.invoke(call).thenApply(result -> {
            if (!result.id().moduleId().equals(candidate.reachable().moduleId())) {
                throw new IllegalStateException("discovered Operation returned foreign value owner");
            }
            if (!result.type().definition().equals(candidate.outputType())) {
                throw new IllegalStateException("discovered Operation returned another nominal contract");
            }
            return result;
        });
    }
}
