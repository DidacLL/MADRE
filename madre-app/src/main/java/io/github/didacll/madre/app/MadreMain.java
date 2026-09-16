package io.github.didacll.madre.app;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.module.OwnerMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletionException;

/** Replaceable local console plus host-owned bootstrap, diagnostics and product-management entrypoints. */
public final class MadreMain {
    private MadreMain() { }

    public static void main(String[] arguments) throws IOException {
        final ParsedArguments parsed;
        try {
            parsed = parse(arguments);
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            usage();
            System.exit(2);
            return;
        }

        HostEnvironment host = HostEnvironment.resolve();
        if (parsed.configuration().isEmpty() && !host.hasPackagedDefaults()) {
            usage();
            System.exit(2);
            return;
        }
        HostEnvironment.LoadedConfiguration loaded = host.loadConfiguration(parsed.configuration());
        Properties properties = loaded.properties();
        List<String> command = parsed.command();
        if (!command.isEmpty() && command.get(0).equals("reasoning")) {
            try {
                ReasoningCli.run(host, loaded, properties, command.subList(1, command.size()));
            } catch (IllegalArgumentException | IllegalStateException exception) {
                System.err.println("reasoning configuration failure: " + message(exception));
                System.exit(2);
            }
            return;
        }

        final MadreApplication application;
        try {
            application = MadreApplication.start(properties);
        } catch (RuntimeException exception) {
            System.err.println("MADRE startup failure: " + message(exception));
            System.exit(2);
            return;
        }
        try (application) {
            if (command.isEmpty()) {
                runConsole(application);
            } else if (command.size() == 1 && command.get(0).equals("doctor")) {
                doctor(host, loaded, application, properties);
            } else if (command.size() == 1 && command.get(0).equals("--list-modules")) {
                printModules(application);
            } else if (command.size() == 6 && command.get(0).equals("--invoke-public")) {
                invoke(application, Invocation.PUBLIC, command.get(1), command.get(2), command.get(3),
                        command.get(4), command.get(5), true);
            } else if (command.size() == 6 && command.get(0).equals("--invoke-owner")) {
                invoke(application, Invocation.OWNER_LOCAL, command.get(1), command.get(2),
                        command.get(3), command.get(4), command.get(5), true);
            } else {
                usage();
                System.exit(2);
            }
        }
    }

    private static ParsedArguments parse(String[] arguments) {
        List<String> values = new ArrayList<>(List.of(arguments));
        Optional<Path> configuration = Optional.empty();
        if (!values.isEmpty() && values.get(0).equals("--config")) {
            if (values.size() < 2 || values.get(1).isBlank()) {
                throw new IllegalArgumentException("--config requires a properties-file path");
            }
            configuration = Optional.of(Path.of(values.remove(1)));
            values.remove(0);
        } else if (!values.isEmpty() && !values.get(0).startsWith("--")
                && !values.get(0).equals("doctor") && !values.get(0).equals("reasoning")) {
            configuration = Optional.of(Path.of(values.remove(0)));
        }
        return new ParsedArguments(configuration, List.copyOf(values));
    }

    private static void doctor(HostEnvironment host, HostEnvironment.LoadedConfiguration loaded,
            MadreApplication application, Properties properties) {
        System.out.println("MADRE doctor");
        System.out.println("version\t" + version());
        System.out.println("java.version\t" + System.getProperty("java.version"));
        System.out.println("java.home\t" + Path.of(System.getProperty("java.home"))
                .toAbsolutePath().normalize());
        System.out.println("configuration\t" + loaded.path());
        String configurationState = loaded.explicit() ? "loaded (explicit)"
                : loaded.bootstrapped() ? "loaded (bootstrapped)" : "loaded (existing)";
        System.out.println("configuration.status\t" + configurationState);
        System.out.println("data.directory\t" + host.dataDirectory());
        System.out.println("state.directory\t" + host.stateDirectory());
        for (Path path : moduleArtifactDirectories(properties, host)) {
            System.out.println("module.artifacts\t" + path);
        }
        for (Path path : reasoningArtifactDirectories(properties, host)) {
            System.out.println("reasoning.artifacts\t" + path);
        }
        System.out.println("module.discovery\tinitialized");
        System.out.println("modules.count\t" + application.installedModules().size());
        application.installedModules().stream().map(module -> module.id().value()).sorted()
                .forEach(id -> System.out.println("module\t" + id));
        String configuredCore = properties.getProperty("roles.core");
        if (configuredCore == null || configuredCore.isBlank()) {
            System.out.println("core\tnot configured");
        } else if (application.resolvedCore().isPresent()) {
            System.out.println("core\tresolved " + application.resolvedCore().orElseThrow().value());
        } else {
            System.out.println("core\tconfigured but unresolved " + configuredCore.strip());
        }
        System.out.println("reasoning.discovery\tinitialized");
        var providers = application.installedReasoningProviders();
        System.out.println("reasoning.providers.count\t" + providers.size());
        providers.forEach(provider -> System.out.println("reasoning.provider\t" + provider.id()));
        var instances = application.configuredReasoningInstances();
        System.out.println("reasoning.instances.count\t" + instances.size());
        instances.forEach(item -> System.out.println("reasoning.instance\t" + item.providerId()
                + "/" + item.instance().name() + "\t"
                + (item.instance().enabled() ? "enabled" : "disabled")));
        var reasoningIds = application.kernel().reasoningCapabilities().installedIds();
        System.out.println("reasoning.mechanisms.count\t" + reasoningIds.size());
        reasoningIds.forEach(id -> System.out.println("reasoning.mechanism\t" + id.value()));
    }

    private static List<Path> moduleArtifactDirectories(Properties properties, HostEnvironment host) {
        String configured = properties.getProperty("modules.directory");
        return configured == null || configured.isBlank()
                ? host.moduleDirectories()
                : List.of(Path.of(configured.strip()).toAbsolutePath().normalize());
    }

    private static List<Path> reasoningArtifactDirectories(Properties properties,
            HostEnvironment host) {
        String configured = properties.getProperty("reasoning.directory");
        return configured == null || configured.isBlank()
                ? host.reasoningDirectories()
                : List.of(Path.of(configured.strip()).toAbsolutePath().normalize());
    }

    private static String version() {
        String version = MadreMain.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "development" : version;
    }

    private static void runConsole(MadreApplication application) throws IOException {
        Sensitivity currentSensitivity = Sensitivity.S5;
        boolean conversational = application.hasOwnerInteraction();
        if (application.kernel().reasoningCapabilities().installedIds().isEmpty()
                && !application.installedReasoningProviders().isEmpty()) {
            System.out.println("No reasoning mechanisms are enabled. Run 'madre reasoning providers' "
                    + "to inspect installed provider types and 'madre reasoning configure ...' to set one up.");
        }
        if (conversational) {
            String core = application.resolvedCore().map(ModuleId::value).orElse("unresolved");
            System.out.println("MADRE ready - conversation via CORE " + core
                    + " (input sensitivity " + currentSensitivity
                    + "); /sensitivity <S1..S5>; /modules; /invoke-owner ...; "
                    + "/invoke-public ...; /exit");
        } else {
            System.out.println("MADRE ready - no conversational CORE; /modules; "
                    + "/invoke-public <module> <operation> <material-type> <S1..S5> <payload>; "
                    + "/invoke-owner <module> <operation> <material-type> <S1..S5> <payload>; /exit");
        }

        OwnerInteractionPresentation presentation = conversational
                ? new OwnerInteractionPresentation(application,
                        text -> System.out.println("follow-up\t" + text))
                : null;
        try (presentation;
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String input = line.strip();
                if (input.isEmpty()) continue;
                if (input.equals("/exit") || input.equals("/quit")) break;
                if (input.equals("/modules")) {
                    printModules(application);
                    continue;
                }
                if (input.startsWith("/invoke-public ")) {
                    invokeConsole(application, Invocation.PUBLIC,
                            input.substring("/invoke-public ".length()));
                    continue;
                }
                if (input.startsWith("/invoke-owner ")) {
                    invokeConsole(application, Invocation.OWNER_LOCAL,
                            input.substring("/invoke-owner ".length()));
                    continue;
                }
                if (input.startsWith("/invoke ")) {
                    invokeConsole(application, Invocation.PUBLIC,
                            input.substring("/invoke ".length()));
                    continue;
                }
                if (input.startsWith("/sensitivity ") || input.equals("/sensitivity")) {
                    if (!conversational) {
                        System.err.println("no owner-interaction Agent is available in the selected CORE");
                        continue;
                    }
                    String value = input.equals("/sensitivity") ? ""
                            : input.substring("/sensitivity ".length()).strip();
                    try {
                        currentSensitivity = ownerSensitivity(value);
                        System.out.println("input sensitivity\t" + currentSensitivity.name());
                    } catch (IllegalArgumentException exception) {
                        System.err.println("sensitivity failure: " + exception.getMessage());
                    }
                    continue;
                }
                if (input.startsWith("/")) {
                    System.err.println("unknown command: " + input);
                    continue;
                }
                if (!conversational) {
                    System.err.println("no owner-interaction Agent is available in the selected CORE; "
                            + "use /invoke-owner for diagnostic Module invocation");
                    continue;
                }
                converse(application, input, currentSensitivity);
            }
        }
    }

    private static void converse(MadreApplication application, String ownerText,
            Sensitivity sensitivity) {
        try {
            OwnerMessage result = application.converse(ownerText, sensitivity)
                    .toCompletableFuture().join();
            System.out.println(result.text());
        } catch (RuntimeException exception) {
            Throwable cause = exception instanceof CompletionException
                    && exception.getCause() != null ? exception.getCause() : exception;
            String message = cause.getMessage() == null ? cause.getClass().getSimpleName()
                    : cause.getMessage();
            System.err.println("conversation failure: " + message);
        }
    }

    private static void invokeConsole(MadreApplication application, Invocation invocation,
            String arguments) {
        String[] fields = arguments.split("\\s+", 5);
        if (fields.length != 5) {
            System.err.println("invoke requires <module> <operation> <material-type> "
                    + "<S1..S5> <payload>");
            return;
        }
        invoke(application, invocation, fields[0], fields[1], fields[2], fields[3], fields[4],
                false);
    }

    private static void printModules(MadreApplication application) {
        application.installedModules().forEach(module -> {
            String role = application.resolvedCore().filter(module.id()::equals).isPresent()
                    ? " [CORE]" : "";
            System.out.println(module.id().value() + " " + module.version() + role);
            module.operations().values().stream()
                    .filter(operation -> module.exposedOperations().contains(operation.id()))
                    .sorted(java.util.Comparator.comparing(operation -> operation.id().name()))
                    .forEach(operation -> System.out.println("  " + operation.id().name()));
        });
    }

    private static void invoke(MadreApplication application, Invocation invocation, String module,
            String operation, String materialType, String sensitivityValue, String payload,
            boolean failFast) {
        try {
            Sensitivity sensitivity = ownerSensitivity(sensitivityValue);
            Material<?> result = switch (invocation) {
                case PUBLIC -> application.invokePublicText(new ModuleId(module), operation,
                        materialType, sensitivity, payload).toCompletableFuture().join();
                case OWNER_LOCAL -> application.invokeOwnerText(new ModuleId(module), operation,
                        materialType, sensitivity, payload).toCompletableFuture().join();
            };
            if (invocation == Invocation.PUBLIC) {
                System.out.println(result.payload());
            } else {
                System.out.println(result.sensitivity().name() + "\t" + result.payload());
            }
        } catch (RuntimeException exception) {
            Throwable cause = exception instanceof CompletionException
                    && exception.getCause() != null ? exception.getCause() : exception;
            String message = cause.getMessage() == null ? cause.getClass().getSimpleName()
                    : cause.getMessage();
            System.err.println("operation failure: " + message);
            if (failFast) throw exception;
        }
    }

    private static Sensitivity ownerSensitivity(String sensitivityValue) {
        if (sensitivityValue == null || sensitivityValue.isBlank()) {
            throw new IllegalArgumentException("Sensitivity must be one of S1..S5");
        }
        final Sensitivity sensitivity;
        try {
            sensitivity = Sensitivity.valueOf(sensitivityValue.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Sensitivity must be one of S1..S5", exception);
        }
        if (sensitivity == Sensitivity.SYSTEM_RESERVED) {
            throw new IllegalArgumentException("SYSTEM_RESERVED is not owner Material");
        }
        return sensitivity;
    }

    private static String message(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && (current.getMessage() == null
                || current.getMessage().isBlank())) current = current.getCause();
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static void usage() {
        System.err.println("usage: madre [--config <path-to-madre.properties>] "
                + "[doctor | reasoning <command> | --list-modules | --invoke-public <module> <operation> "
                + "<material-type> <S1..S5> <payload> | --invoke-owner <module> <operation> "
                + "<material-type> <S1..S5> <payload>]");
        ReasoningCli.usage();
        System.err.println("legacy developer usage: madre <path-to-madre.properties> ...");
    }

    private record ParsedArguments(Optional<Path> configuration, List<String> command) { }
    private enum Invocation { PUBLIC, OWNER_LOCAL }
}
