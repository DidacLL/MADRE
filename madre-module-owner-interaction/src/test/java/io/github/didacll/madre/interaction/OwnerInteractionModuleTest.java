package io.github.didacll.madre.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.didacll.madre.sdk.module.ConversationalAgent;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class OwnerInteractionModuleTest {
    @Test
    void isAnOrdinarySdkModuleWithOneConversationalAgent() {
        OwnerInteractionModule module = new OwnerInteractionModule();
        ModuleInstance installed = ModuleInstance.from(module);

        assertEquals(OwnerInteractionModule.ID, installed.definition().id());
        assertEquals(Set.of(OwnerInteractionModule.RESPOND),
                installed.definition().exposedOperations());
        assertEquals(1, installed.agents().size());
        assertInstanceOf(ConversationalAgent.class,
                installed.agents().get(OwnerInteractionModule.CONVERSATION_AGENT));
    }
}
