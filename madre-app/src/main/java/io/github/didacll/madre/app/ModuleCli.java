package io.github.didacll.madre.app;

import io.github.didacll.madre.sdk.registration.ModuleConfigurationDescriptor;
import io.github.didacll.madre.sdk.registration.ModuleConfigurationField;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Host-owned generic command surface for Module configuration and local artifact lifecycle. */
final class ModuleCli {
    private ModuleCli() { }

    static void run(HostEnvironment host, HostEnvironment.LoadedConfiguration loaded,
            Properties properties, List<String> arguments) throws IOException {
        if (arguments.isEmpty() || arguments.equals(List.of("help"))) {
            usage();
            return;
        }
        switch (arguments.get(0)) {
            case "install" -> install(host, loaded, properties, arguments);
            case "uninstall" -> uninstall(host, loaded, properties, arguments);
            default -> runDiscoveryCommand(host, loaded, properties, arguments);
        }
    }

    private static void runDiscoveryCommand(HostEnvironment host,
            HostEnvironment.LoadedConfiguration loaded, Properties properties,
            List<String> arguments) throws IOException {
        List<Path> directories = moduleArtifactDirectories(properties, host);
        try (InstalledModuleLoader loader = new InstalledModuleLoader(directories)) {
            ModuleConfigurationManager manager = new ModuleConfigurationManager(
                    loaded.path(), properties, loader.providers());
            switch (arguments.get(0)) {
                case "list" -> list(host, loader, manager);
                case "inspect" -> inspect(arguments, manager);
                case "configure" -> configure(arguments, manager);
                default -> throw new IllegalArgumentException(
                        "unknown modules command: " + arguments.get(0));
            }
        }
    }

    private static void install(HostEnvironment host, HostEnvironment.LoadedConfiguration loaded,
            Properties properties, List<String> arguments) throws IOException {
        if (arguments.size() < 2 || arguments.size() > 3
                || (arguments.size() == 3 && !arguments.get(2).equals("--replace"))) {
            throw new IllegalArgumentException("modules install requires <jar> [--replace]");
        }
        boolean replace = arguments.size() == 3;
        ModuleArtifactLifecycle.InstallResult result = ModuleArtifactLifecycle.install(host, loaded,
                properties, Path.of(arguments.get(1)), replace);
        System.out.println((replace ? "module.replaced\t" : "module.installed\t")
                + result.moduleId() + "\tsource=owner\tartifact=" + result.path().getFileName()
                + "\tsha256=" + result.sha256());
    }

    private static void uninstall(HostEnvironment host, HostEnvironment.LoadedConfiguration loaded,
            Properties properties, List<String> arguments) throws IOException {
        if (arguments.size() < 2 || arguments.size() > 3
                || (arguments.size() == 3 && !arguments.get(2).equals("--purge-configuration"))) {
            throw new IllegalArgumentException(
                    "modules uninstall requires <module-id> [--purge-configuration]");
        }
        boolean purge = arguments.size() == 3;
        ModuleArtifactLifecycle.UninstallResult result = ModuleArtifactLifecycle.uninstall(host, loaded,
                properties, arguments.get(1), purge);
        System.out.println("module.uninstalled\t" + result.moduleId()
                + "\tconfiguration-purged=" + result.configurationPurged());
    }

    private static void list(HostEnvironment host, InstalledModuleLoader loader,
            ModuleConfigurationManager manager) {
        List<ModuleProvider> providers = manager.providers();
        System.out.println("modules.count\t" + providers.size());
        for (ModuleProvider provider : providers) {
            ModuleConfigurationDescriptor descriptor = manager.descriptor(provider);
            InstalledModuleLoader.DiscoveredProvider discovered = loader.discoveredProviders().stream()
                    .filter(item -> item.provider() == provider).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "cannot resolve source JAR for Module " + provider.moduleId()));
            ModuleArtifactLifecycle.Source source = ModuleArtifactLifecycle.classify(host,
                    provider.moduleId(), discovered.sourceJar());
            String artifact = source == ModuleArtifactLifecycle.Source.OWNER
                    ? "\tartifact=" + discovered.sourceJar().getFileName() : "";
            System.out.println("module\t" + provider.moduleId() + "\t" + descriptor.displayName()
                    + "\tconfigurable-fields=" + descriptor.fields().size()
                    + "\tsource=" + source.label() + artifact);
        }
    }

    private static void inspect(List<String> arguments, ModuleConfigurationManager manager) {
        if (arguments.size() != 2) {
            throw new IllegalArgumentException("modules inspect requires <module-id>");
        }
        ModuleProvider provider = manager.provider(arguments.get(1));
        ModuleConfigurationDescriptor descriptor = manager.descriptor(provider);
        ModuleProviderConfiguration configuration = manager.configuration(provider);
        System.out.println("module.id\t" + provider.moduleId());
        System.out.println("module.display-name\t" + descriptor.displayName());
        System.out.println("module.help\t" + descriptor.help());
        for (ModuleConfigurationField field : descriptor.fields()) {
            StringBuilder detail = new StringBuilder("module.field\t").append(field.name())
                    .append('\t').append(field.kind())
                    .append('\t').append(field.required() ? "required" : "optional");
            field.defaultValue().ifPresent(value -> detail.append("\tdefault=").append(value));
            if (!field.allowedValues().isEmpty()) {
                detail.append("\tchoices=").append(String.join(",", field.allowedValues()));
            }
            if (field.minimum().isPresent()) detail.append("\tmin=").append(field.minimum().getAsLong());
            if (field.maximum().isPresent()) detail.append("\tmax=").append(field.maximum().getAsLong());
            detail.append("\tcurrent=")
                    .append(configuration.value(field.name()).orElse("<unset>"));
            System.out.println(detail);
            System.out.println("  " + field.displayName() + ": " + field.help());
        }
    }

    private static void configure(List<String> arguments, ModuleConfigurationManager manager)
            throws IOException {
        if (arguments.size() < 2) {
            throw new IllegalArgumentException(
                    "modules configure requires <module-id> [--set <field>=<value>]...");
        }
        ModuleProvider provider = manager.provider(arguments.get(1));
        Map<String, String> values = arguments.size() == 2
                ? interactiveValues(provider, manager)
                : suppliedValues(arguments.subList(2, arguments.size()));
        manager.configure(provider.moduleId().value(), values);
        System.out.println("module.configured\t" + provider.moduleId());
    }

    private static Map<String, String> interactiveValues(ModuleProvider provider,
            ModuleConfigurationManager manager) throws IOException {
        ModuleConfigurationDescriptor descriptor = manager.descriptor(provider);
        ModuleProviderConfiguration existing = manager.configuration(provider);
        Map<String, String> values = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("Configure " + descriptor.displayName()
                    + ". Press Enter to keep the current/default value.");
            for (ModuleConfigurationField field : descriptor.fields()) {
                String current = existing.value(field.name()).orElse(null);
                String suggested = current != null ? current : field.defaultValue().orElse(null);
                String choices = field.allowedValues().isEmpty() ? ""
                        : " choices=" + String.join(",", field.allowedValues());
                System.out.print(field.displayName() + " [" + field.name() + ", " + field.kind()
                        + choices + (suggested == null ? "" : ", current/default=" + suggested) + "]: ");
                System.out.flush();
                String entered = reader.readLine();
                if (entered == null) {
                    throw new IllegalArgumentException("configuration input ended early");
                }
                if (!entered.isBlank()) values.put(field.name(), entered);
                else if (current == null && field.defaultValue().isPresent()) {
                    values.put(field.name(), field.defaultValue().orElseThrow());
                }
            }
        }
        return values;
    }

    private static Map<String, String> suppliedValues(List<String> arguments) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < arguments.size(); index += 2) {
            if (index + 1 >= arguments.size() || !arguments.get(index).equals("--set")) {
                throw new IllegalArgumentException(
                        "modules configure options must be repeated --set <field>=<value>");
            }
            String assignment = arguments.get(index + 1);
            int separator = assignment.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("--set requires <field>=<value>");
            }
            String field = assignment.substring(0, separator).strip();
            String value = assignment.substring(separator + 1);
            if (values.put(field, value) != null) {
                throw new IllegalArgumentException("configuration field supplied more than once: " + field);
            }
        }
        return Map.copyOf(values);
    }

    private static List<Path> moduleArtifactDirectories(Properties properties, HostEnvironment host) {
        String configured = properties.getProperty("modules.directory");
        return configured == null || configured.isBlank()
                ? host.moduleDirectories()
                : List.of(Path.of(configured.strip()).toAbsolutePath().normalize());
    }

    static void usage() {
        System.err.println("Module management commands:");
        System.err.println("  madre modules list");
        System.err.println("  madre modules inspect <module-id>");
        System.err.println("  madre modules configure <module-id> [--set <field>=<value>]...");
        System.err.println("  madre modules install <jar> [--replace]");
        System.err.println("  madre modules uninstall <module-id> [--purge-configuration]");
    }
}
