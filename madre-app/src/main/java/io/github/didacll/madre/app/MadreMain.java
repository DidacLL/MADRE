package io.github.didacll.madre.app;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.material.Material;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletionException;

/** Replaceable local console over installed Module discovery and distinct invocation boundaries. */
public final class MadreMain {
    private MadreMain() { }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length < 1) {
            usage();
            System.exit(2);
        }
        Properties properties = new Properties();
        try (var input = Files.newInputStream(Path.of(arguments[0]))) {
            properties.load(input);
        }
        try (MadreApplication application = MadreApplication.start(properties)) {
            if (arguments.length == 1) {
                runConsole(application);
            } else if (arguments.length == 2 && arguments[1].equals("--list-modules")) {
                printModules(application);
            } else if (arguments.length == 7
                    && arguments[1].equals("--invoke-public")) {
                invoke(application, Invocation.PUBLIC, arguments[2], arguments[3], arguments[4],
                        arguments[5], arguments[6], true);
            } else if (arguments.length == 7
                    && arguments[1].equals("--invoke-owner")) {
                invoke(application, Invocation.OWNER_LOCAL, arguments[2], arguments[3],
                        arguments[4], arguments[5], arguments[6], true);
            } else {
                usage();
                System.exit(2);
            }
        }
    }

    private static void runConsole(MadreApplication application) throws IOException {
        Optional<LocalInteractionBinding> configured = application.interactionBinding();
        Sensitivity currentSensitivity = configured.map(LocalInteractionBinding::defaultSensitivity)
                .orElse(Sensitivity.S1);
        if (configured.isPresent()) {
            LocalInteractionBinding binding = configured.orElseThrow();
            System.out.println("MADRE ready — local text -> " + binding.moduleId().value() + "/"
                    + binding.defaultOperation() + " (owner-local, " + currentSensitivity
                    + "); /standard <text>; /updates; /sensitivity <S1..S5>; /modules; "
                    + "/invoke-owner ...; /invoke-public ...; /exit");
        } else {
            System.out.println("MADRE ready — generic console; /modules; "
                    + "/invoke-public <module> <operation> <material-type> <S1..S5> <payload>; "
                    + "/invoke-owner <module> <operation> <material-type> <S1..S5> <payload>; /exit");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
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
                    if (configured.isEmpty()) {
                        System.err.println("interaction is not configured");
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
                if (input.startsWith("/standard ") || input.equals("/standard")) {
                    if (configured.isEmpty()) {
                        System.err.println("interaction is not configured");
                        continue;
                    }
                    String text = input.equals("/standard") ? ""
                            : input.substring("/standard ".length()).strip();
                    if (text.isEmpty()) {
                        System.err.println("standard requires text");
                        continue;
                    }
                    LocalInteractionBinding binding = configured.orElseThrow();
                    invoke(application, Invocation.OWNER_LOCAL, binding.moduleId().value(),
                            binding.standardOperation(), binding.promptMaterialType(),
                            currentSensitivity.name(), text, false);
                    continue;
                }
                if (input.equals("/updates")) {
                    if (configured.isEmpty()) {
                        System.err.println("interaction is not configured");
                        continue;
                    }
                    LocalInteractionBinding binding = configured.orElseThrow();
                    if (binding.updates().isEmpty()) {
                        System.err.println("interaction background updates are not configured");
                        continue;
                    }
                    LocalInteractionBinding.UpdatesBinding updates = binding.updates().orElseThrow();
                    invoke(application, Invocation.OWNER_LOCAL, binding.moduleId().value(),
                            updates.operation(), updates.materialType(), updates.sensitivity().name(),
                            updates.payload(), false);
                    continue;
                }
                if (input.startsWith("/")) {
                    System.err.println("unknown command: " + input);
                    continue;
                }
                if (configured.isEmpty()) {
                    System.err.println("interaction is not configured; use /invoke-owner for local Module invocation");
                    continue;
                }
                LocalInteractionBinding binding = configured.orElseThrow();
                invoke(application, Invocation.OWNER_LOCAL, binding.moduleId().value(),
                        binding.defaultOperation(), binding.promptMaterialType(),
                        currentSensitivity.name(), input, false);
            }
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
                    .filter(operation -> operation.visibility()
                            == io.github.didacll.madre.sdk.module.OperationVisibility.PUBLIC)
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
            if (invocation == Invocation.OWNER_LOCAL) {
                System.out.println(result.sensitivity().name() + "\t" + result.payload());
            } else {
                System.out.println(result.payload());
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

    private static void usage() {
        System.err.println("usage: madre <path-to-madre.properties> "
                + "[--list-modules | --invoke-public <module> <operation> "
                + "<material-type> <S1..S5> <payload> | --invoke-owner <module> <operation> "
                + "<material-type> <S1..S5> <payload>]");
    }

    private enum Invocation { PUBLIC, OWNER_LOCAL }
}
