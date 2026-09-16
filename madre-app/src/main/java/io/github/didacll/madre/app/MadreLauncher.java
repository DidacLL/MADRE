package io.github.didacll.madre.app;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Product launcher that routes host-management commands before semantic application startup. */
public final class MadreLauncher {
    private MadreLauncher() { }

    public static void main(String[] arguments) throws IOException {
        ModuleCommand command = moduleCommand(arguments);
        if (command == null) {
            MadreMain.main(arguments);
            return;
        }
        HostEnvironment host = HostEnvironment.resolve();
        if (command.configuration().isEmpty() && !host.hasPackagedDefaults()) {
            ModuleCli.usage();
            System.exit(2);
            return;
        }
        try {
            HostEnvironment.LoadedConfiguration loaded = host.loadConfiguration(command.configuration());
            ModuleCli.run(host, loaded, loaded.properties(), command.arguments());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.err.println("Module configuration failure: " + message(exception));
            System.exit(2);
        }
    }

    private static ModuleCommand moduleCommand(String[] arguments) {
        List<String> values = Arrays.asList(arguments);
        if (!values.isEmpty() && values.get(0).equals("modules")) {
            return new ModuleCommand(Optional.empty(), List.copyOf(values.subList(1, values.size())));
        }
        if (values.size() >= 3 && values.get(0).equals("--config")
                && values.get(2).equals("modules")) {
            if (values.get(1).isBlank()) {
                throw new IllegalArgumentException("--config requires a properties-file path");
            }
            return new ModuleCommand(Optional.of(Path.of(values.get(1))),
                    List.copyOf(values.subList(3, values.size())));
        }
        if (values.size() >= 2 && !values.get(0).startsWith("--")
                && values.get(1).equals("modules")) {
            return new ModuleCommand(Optional.of(Path.of(values.get(0))),
                    List.copyOf(values.subList(2, values.size())));
        }
        return null;
    }

    private static String message(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && (current.getMessage() == null
                || current.getMessage().isBlank())) current = current.getCause();
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record ModuleCommand(Optional<Path> configuration, List<String> arguments) { }
}
