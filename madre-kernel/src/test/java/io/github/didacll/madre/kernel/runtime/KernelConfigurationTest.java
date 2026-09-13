package io.github.didacll.madre.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.didacll.madre.kernel.config.KernelConfiguration;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class KernelConfigurationTest {
    @Test void parsesOnlyExplicitRuntimeFacts() {
        Properties values = new Properties(); values.setProperty("roles.core", "owner.core");
        values.setProperty("kernel.database", "kernel.sqlite"); values.setProperty("resources.model-slot", "1");
        KernelConfiguration configuration = KernelConfiguration.from(values);
        assertEquals("owner.core", configuration.coreModule().value()); assertEquals(1L, configuration.resourceCapacity().values().iterator().next());
    }
}
