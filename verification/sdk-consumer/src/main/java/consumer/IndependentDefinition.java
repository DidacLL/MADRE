package consumer;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.MaterialCodec;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.AgentDefinition;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/** Compile-only proof that the published SDK is sufficient without Kernel or CORE. */
public final class IndependentDefinition {
    private IndependentDefinition() { }

    public static ModuleDefinition define() {
        ModuleId id = new ModuleId("phd.module");
        MaterialCodec<String> codec = new MaterialCodec<>() {
            @Override public byte[] encode(String value) {
                return value.getBytes(StandardCharsets.UTF_8);
            }
            @Override public String decode(byte[] bytes) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
        };
        MaterialType<String> note = new MaterialType<>(new MaterialTypeId(id, "note"),
                String.class, "text/plain", codec);
        OperationId operationId = new OperationId(id, "inspect");
        OperationDefinition<String, Void> operation = new OperationDefinition<>(operationId,
                "Inspect a note", OperationVisibility.PUBLIC,
                Map.of(note.id(), Privacy.P5), Map.of(), Map.of());
        AgentId agentId = new AgentId(id, "researcher");
        AgentDefinition agent = new AgentDefinition(agentId, "Research behavior",
                Integrity.I4, Set.of(), Map.of(), Set.of(operationId));
        return new ModuleDefinition(id, "1.0.0", "Independent PhD Module",
                Map.of(note.id(), note), Set.of(), Map.of(agentId, agent), Map.of(),
                Map.of(operationId, operation));
    }
}
