package io.github.didacll.madre.interaction;

import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.module.Module;
import io.github.didacll.madre.sdk.registration.ModuleConfigurationDescriptor;
import io.github.didacll.madre.sdk.registration.ModuleConfigurationField;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import java.util.List;
import java.util.OptionalLong;

/** Installation entrypoint for the shipped ordinary owner-interaction Module. */
public final class OwnerInteractionModuleProvider implements ModuleProvider {
    private static final ModuleConfigurationDescriptor CONFIGURATION =
            new ModuleConfigurationDescriptor(OwnerInteractionModule.ID, "Owner interaction",
                    "Controls conversation state plus foreground and durable-background reasoning preferences used by the shipped owner-interaction Module.",
                    List.of(
                            ModuleConfigurationField.integer("foreground-maximum-tokens",
                                    "Foreground maximum tokens", "Maximum generated tokens for foreground requests.",
                                    false, "256", OptionalLong.of(1), OptionalLong.of(Integer.MAX_VALUE)),
                            ModuleConfigurationField.integer("background-maximum-tokens",
                                    "Background maximum tokens", "Maximum generated tokens for durable background requests.",
                                    false, "512", OptionalLong.of(1), OptionalLong.of(Integer.MAX_VALUE)),
                            ModuleConfigurationField.integer("conversation-history-exchanges",
                                    "Conversation history exchanges", "Maximum completed owner/assistant exchanges retained by this Agent and reused as semantic context.",
                                    false, "4", OptionalLong.of(1), OptionalLong.of(Integer.MAX_VALUE)),
                            ModuleConfigurationField.integer("foreground-timeout-ms",
                                    "Foreground timeout (ms)", "Foreground reasoning timeout in integer milliseconds.",
                                    false, "90000", OptionalLong.of(1), OptionalLong.empty()),
                            ModuleConfigurationField.integer("background-timeout-ms",
                                    "Background timeout (ms)", "Durable background reasoning timeout in integer milliseconds.",
                                    false, "300000", OptionalLong.of(1), OptionalLong.empty()),
                            ModuleConfigurationField.integer("background-retry-attempts",
                                    "Background retry attempts", "Maximum attempts for durable background reasoning.",
                                    false, "3", OptionalLong.of(1), OptionalLong.of(Integer.MAX_VALUE)),
                            ModuleConfigurationField.integer("background-retry-delay-ms",
                                    "Background retry delay (ms)", "Delay between durable background attempts in integer milliseconds.",
                                    false, "5000", OptionalLong.of(0), OptionalLong.empty()),
                            ModuleConfigurationField.choice("foreground-location",
                                    "Foreground location", "Optional reasoning location preference for foreground requests.",
                                    false, null, List.of("LOCAL", "REMOTE")),
                            ModuleConfigurationField.integer("foreground-maximum-latency-ms",
                                    "Foreground maximum latency (ms)", "Optional maximum acceptable foreground reasoning latency.",
                                    false, null, OptionalLong.of(1), OptionalLong.empty()),
                            ModuleConfigurationField.choice("background-location",
                                    "Background location", "Optional reasoning location preference for durable background requests.",
                                    false, null, List.of("LOCAL", "REMOTE")),
                            ModuleConfigurationField.integer("background-maximum-latency-ms",
                                    "Background maximum latency (ms)", "Optional maximum acceptable durable-background reasoning latency.",
                                    false, null, OptionalLong.of(1), OptionalLong.empty())));

    @Override public ModuleId moduleId() { return OwnerInteractionModule.ID; }

    @Override public ModuleConfigurationDescriptor configurationDescriptor() { return CONFIGURATION; }

    @Override public ModuleProviderConfiguration validateConfiguration(
            ModuleProviderConfiguration configuration) {
        OwnerInteractionSettings.fromInstallation(configuration);
        return configuration;
    }

    @Override public Module create(ModuleContext context,
            ModuleProviderConfiguration configuration) {
        java.util.Objects.requireNonNull(context, "context");
        OwnerInteractionSettings settings = OwnerInteractionSettings.fromInstallation(configuration);
        return new OwnerInteractionModule(context.reasoning(),
                context.stateDirectory().resolve("owner-interaction-background.state"), settings);
    }
}
