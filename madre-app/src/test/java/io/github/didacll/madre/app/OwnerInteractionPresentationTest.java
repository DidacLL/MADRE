package io.github.didacll.madre.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialCodecs;
import io.github.didacll.madre.sdk.material.MaterialType;
import org.junit.jupiter.api.Test;

final class OwnerInteractionPresentationTest {
    private static final ModuleId MODULE = new ModuleId("test.interaction.presentation");
    private static final MaterialType<String> UPDATES = new MaterialType<>(
            new MaterialTypeId(MODULE, "updates"), String.class, "text/plain; charset=utf-8",
            MaterialCodecs.utf8String());

    @Test void stripsModuleAssociationHandleAndKeepsUsefulFollowUpText() {
        Material<String> update = material("work-123\tA useful correction", Sensitivity.S4);
        assertEquals("A useful correction",
                OwnerInteractionPresentation.visiblePayload(update).orElseThrow());
    }

    @Test void suppressesModuleDecisionThatNoFollowUpIsUseful() {
        assertTrue(OwnerInteractionPresentation.visiblePayload(
                material("work-123\tNO_FOLLOW_UP", Sensitivity.S3)).isEmpty());
    }

    @Test void preservesSeveralUsefulFollowUpsWithoutExposingWorkHandles() {
        Material<String> update = material(
                "work-a\tFirst improvement\nwork-b\tSecond improvement", Sensitivity.S5);
        String visible = OwnerInteractionPresentation.visiblePayload(update).orElseThrow();
        assertTrue(visible.contains("First improvement"));
        assertTrue(visible.contains("Second improvement"));
        assertTrue(!visible.contains("work-a") && !visible.contains("work-b"));
    }

    private static Material<String> material(String text, Sensitivity sensitivity) {
        return new Material<>(new MaterialId(MODULE, "material"), UPDATES, text, sensitivity);
    }
}
