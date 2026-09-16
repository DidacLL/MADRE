package io.github.didacll.madre.sdk.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.sdk.identity.ModuleId;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

final class ModuleConfigurationDescriptorTest {
    @Test void keepsFieldsImmutableAndRejectsDuplicateNames() {
        ModuleId id = new ModuleId("fixture.module");
        List<ModuleConfigurationField> fields = new ArrayList<>();
        fields.add(ModuleConfigurationField.text("name", "Name", "Help", false, null));
        ModuleConfigurationDescriptor descriptor = new ModuleConfigurationDescriptor(
                id, "Fixture", "Fixture configuration.", fields);
        fields.clear();

        assertEquals(1, descriptor.fields().size());
        assertThrows(UnsupportedOperationException.class,
                () -> descriptor.fields().add(ModuleConfigurationField.text(
                        "other", "Other", "Help", false, null)));
        assertThrows(IllegalArgumentException.class,
                () -> new ModuleConfigurationDescriptor(id, "Fixture", "Help", List.of(
                        ModuleConfigurationField.text("same", "One", "Help", false, null),
                        ModuleConfigurationField.text("same", "Two", "Help", false, null))));
    }

    @Test void choiceAndIntegerFieldsEnforceTheirDeclaredShape() {
        ModuleConfigurationField choice = ModuleConfigurationField.choice("location", "Location",
                "Execution location.", false, null, List.of("LOCAL", "REMOTE"));
        assertEquals("LOCAL", choice.canonicalize(" LOCAL "));
        assertThrows(IllegalArgumentException.class, () -> choice.canonicalize("elsewhere"));

        ModuleConfigurationField integer = ModuleConfigurationField.integer("attempts", "Attempts",
                "Retry attempts.", false, "3", OptionalLong.of(1), OptionalLong.of(5));
        assertEquals("4", integer.canonicalize("04"));
        assertThrows(IllegalArgumentException.class, () -> integer.canonicalize("0"));
        assertThrows(IllegalArgumentException.class, () -> integer.canonicalize("6"));
        assertThrows(IllegalArgumentException.class, () -> integer.canonicalize("many"));
    }
}
