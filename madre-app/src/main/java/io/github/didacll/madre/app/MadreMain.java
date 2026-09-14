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
                        arguments[5], arguments[6]);
            } else if (arguments.length == 7
                    && arguments[1].equals("--invoke-owner")) {
                invoke(application, Invocation.OWNER_LOCAL, arguments[2], arguments[3],
                        arguments[4], arguments[5], arguments[6]);
            } else {
                usage();
                System.exit(2);
            }
        }
    }

    private static void runConsole(MadreApplication application) throws IOException {
        System.out.println("MADRE ready — /modules; "
                + "/invoke-public <module> <operation> <material-type> <S1..S5> <payload>; "
                + "/invoke-owner <module> <operation> <material-type> <S1..S5> <payload>; /exit");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String input = line.strip();
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
                }
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
        invoke(application, invocation, fields[0], fields[1], fields[2], fields[3], fields[4]);
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
            String operation, String materialType, String sensitivityValue, String payload) {
        try {
            Sensitivity sensitivity = Sensitivity.valueOf(
                    sensitivityValue.toUpperCase(Locale.ROOT));
            if (sensitivity == Sensitivity.SYSTEM_RESERVED) {
                throw new IllegalArgumentException("SYSTEM_RESERVED is not owner Material");
            }
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
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            System.err.println("operation failure: " + cause.getMessage());
            throw exception;
        } catch (IllegalArgumentException exception) {
            System.err.println("operation failure: " + exception.getMessage());
            throw exception;
        }
    }

    private static void usage() {
        System.err.println("usage: madre <path-to-madre.properties> "
                + "[--list-modules | --invoke-public <module> <operation> "
                + "<material-type> <S1..S5> <payload> | --invoke-owner <module> <operation> "
                + "<material-type> <S1..S5> <payload>]");
    }

    private enum Invocation { PUBLIC, OWNER_LOCAL }
}
