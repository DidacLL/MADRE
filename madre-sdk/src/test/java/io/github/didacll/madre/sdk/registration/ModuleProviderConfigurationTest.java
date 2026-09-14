package io.github.didacll.madre.sdk.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.sdk.identity.ModuleId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ModuleProviderConfigurationTest {
    @Test void keepsConfigurationScopedToCanonicalModuleIdentityAndReadOnly() {
        ModuleId moduleId = new ModuleId("fixture.module.with.dots");
        ModuleProviderConfiguration configuration = new ModuleProviderConfiguration(moduleId,
                Map.of(" marker ", "value"));

        assertEquals(moduleId, configuration.moduleId());
        assertEquals("value", configuration.value("marker").orElseThrow());
        assertEquals(Set.of("marker"), configuration.keys());
        assertThrows(UnsupportedOperationException.class,
                () -> configuration.keys().remove("marker"));
    }

    @Test void rejectsBlankOrNormalizationCollidingModuleOwnedKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> new ModuleProviderConfiguration(new ModuleId("fixture.module"),
                        Map.of(" ", "value")));

        Map<String, String> values = new LinkedHashMap<>();
        values.put("setting", "one");
        values.put(" setting ", "two");
        IllegalArgumentException collision = assertThrows(IllegalArgumentException.class,
                () -> new ModuleProviderConfiguration(new ModuleId("fixture.module"), values));
        assertTrue(collision.getMessage().contains("collide after normalization"));
    }
}
