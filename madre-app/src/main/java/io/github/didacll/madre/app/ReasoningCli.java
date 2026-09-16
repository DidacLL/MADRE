package io.github.didacll.madre.app;

import io.github.didacll.madre.reasoning.installation.ReasoningConfigurationField;
import io.github.didacll.madre.reasoning.installation.ReasoningConfiguredInstance;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderDescriptor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Host-owned generic command surface for reasoning configuration and local artifact lifecycle. */
final class ReasoningCli {
    private ReasoningCli() { }

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
        List<Path> directories = reasoningArtifactDirectories(properties, host);
        try (InstalledReasoningLoader loader = new InstalledReasoningLoader(directories)) {
            ReasoningConfigurationManager manager =
                    new ReasoningConfigurationManager(loaded.path(), properties, loader);
            switch (arguments.get(0)) {
                case "providers" -> providers(host, loader);
                case "list" -> list(loader, manager);
                case "inspect" -> inspect(arguments, manager);
                case "configure" -> configure(arguments, manager);
                case "enable" -> setEnabled(arguments, manager, true);
                case "disable" -> setEnabled(arguments, manager, false);
                case "remove" -> remove(arguments, manager);
                default -> throw new IllegalArgumentException(
                        "unknown reasoning command: " + arguments.get(0));
            }
        }
    }

    private static void install(HostEnvironment host, HostEnvironment.LoadedConfiguration loaded,
            Properties properties, List<String> arguments) throws IOException {
        if (arguments.size() < 2 || arguments.size() > 3
                || (arguments.size() == 3 && !arguments.get(2).equals("--replace"))) {
            throw new IllegalArgumentException("reasoning install requires <jar> [--replace]");
        }
        boolean replace = arguments.size() == 3;
        ReasoningArtifactLifecycle.InstallResult result = ReasoningArtifactLifecycle.install(host,
                loaded, properties, Path.of(arguments.get(1)), replace);
        System.out.println((replace ? "reasoning.provider.replaced\t" : "reasoning.provider.installed\t")
                + result.providerId() + "\tsource=owner\tartifact=" + result.path().getFileName()
                + "\tsha256=" + result.sha256());
    }

    private static void uninstall(HostEnvironment host, HostEnvironment.LoadedConfiguration loaded,
            Properties properties, List<String> arguments) throws IOException {
        if (arguments.size() < 2 || arguments.size() > 3
                || (arguments.size() == 3 && !arguments.get(2).equals("--purge-configuration"))) {
            throw new IllegalArgumentException(
                    "reasoning uninstall requires <provider-id> [--purge-configuration]");
        }
        boolean purge = arguments.size() == 3;
        ReasoningArtifactLifecycle.UninstallResult result = ReasoningArtifactLifecycle.uninstall(host,
                loaded, properties, arguments.get(1), purge);
        System.out.println("reasoning.provider.uninstalled\t" + result.providerId()
                + "\tconfiguration-purged=" + result.configurationPurged());
    }

    private static void providers(HostEnvironment host, InstalledReasoningLoader loader) {
        List<ReasoningProviderDescriptor> descriptors = loader.providerDescriptors();
        System.out.println("reasoning.providers.count\t" + descriptors.size());
        for (ReasoningProviderDescriptor descriptor : descriptors) {
            InstalledReasoningLoader.DiscoveredProvider discovered = loader.discoveredProviders().stream()
                    .filter(item -> item.provider().descriptor().id().equals(descriptor.id()))
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "cannot resolve source JAR for reasoning provider " + descriptor.id()));
            ReasoningArtifactLifecycle.Source source = ReasoningArtifactLifecycle.classify(host,
                    descriptor.id(), discovered.sourceJar());
            String artifact = source == ReasoningArtifactLifecycle.Source.OWNER
                    ? "\tartifact=" + discovered.sourceJar().getFileName() : "";
            System.out.println("reasoning.provider\t" + descriptor.id() + "\t"
                    + descriptor.displayName() + "\tsource=" + source.label() + artifact);
            System.out.println("  " + descriptor.help());
            for (ReasoningConfigurationField field : descriptor.fields()) {
                StringBuilder detail = new StringBuilder("  field\t").append(field.name())
                        .append('\t').append(field.kind())
                        .append('\t').append(field.required() ? "required" : "optional");
                field.defaultValue().ifPresent(value -> detail.append("\tdefault=").append(value));
                if (!field.allowedValues().isEmpty()) {
                    detail.append("\tchoices=").append(String.join(",", field.allowedValues()));
                }
                if (field.minimum().isPresent()) {
                    detail.append("\tmin=").append(field.minimum().getAsLong());
                }
                if (field.maximum().isPresent()) {
                    detail.append("\tmax=").append(field.maximum().getAsLong());
                }
                System.out.println(detail);
                System.out.println("    " + field.displayName() + ": " + field.help());
            }
        }
    }

    private static void list(InstalledReasoningLoader loader, ReasoningConfigurationManager manager) {
        List<InstalledReasoningLoader.ProviderInstance> instances =
                loader.configuredInstances(manager.configuration());
        System.out.println("reasoning.instances.count\t" + instances.size());
        instances.forEach(item -> System.out.println("reasoning.instance\t" + item.providerId()
                + "/" + item.instance().name() + "\t"
                + (item.instance().enabled() ? "enabled" : "disabled")));
    }

    private static void inspect(List<String> arguments, ReasoningConfigurationManager manager) {
        if (arguments.size() != 2) {
            throw new IllegalArgumentException("reasoning inspect requires <provider>/<instance>");
        }
        ProviderInstanceRef ref = reference(arguments.get(1));
        ReasoningMechanismProvider provider = manager.provider(ref.provider());
        ReasoningConfiguredInstance instance = provider.configurator()
                .configuredInstances(manager.configuration()).stream()
                .filter(item -> item.name().equals(ref.instance())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "reasoning instance is not configured: " + arguments.get(1)));
        System.out.println("reasoning.provider\t" + provider.descriptor().id());
        System.out.println("reasoning.instance\t" + instance.name());
        System.out.println("reasoning.state\t" + (instance.enabled() ? "enabled" : "disabled"));
        for (ReasoningConfigurationField field : provider.descriptor().fields()) {
            System.out.println("reasoning.field\t" + field.name() + "\t"
                    + instance.values().getOrDefault(field.name(), "<unset>"));
        }
    }

    private static void configure(List<String> arguments, ReasoningConfigurationManager manager)
            throws IOException {
        if (arguments.size() < 3) {
            throw new IllegalArgumentException(
                    "reasoning configure requires <provider> <instance> [--set <field>=<value>]...");
        }
        String providerId = arguments.get(1);
        String instance = arguments.get(2);
        ReasoningMechanismProvider provider = manager.provider(providerId);
        Map<String, String> values;
        if (arguments.size() == 3) {
            values = interactiveValues(provider, instance, manager);
        } else {
            values = suppliedValues(arguments.subList(3, arguments.size()));
        }
        manager.configure(providerId, instance, values);
        System.out.println("reasoning.configured\t" + provider.descriptor().id() + "/" + instance
                + "\tenabled");
    }

    private static Map<String, String> interactiveValues(ReasoningMechanismProvider provider,
            String instance, ReasoningConfigurationManager manager) throws IOException {
        Optional<ReasoningConfiguredInstance> existing = provider.configurator()
                .configuredInstances(manager.configuration()).stream()
                .filter(item -> item.name().equals(instance)).findFirst();
        Map<String, String> values = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("Configure " + provider.descriptor().displayName() + " instance " + instance
                    + ". Press Enter to keep the current/default value.");
            for (ReasoningConfigurationField field : provider.descriptor().fields()) {
                String current = existing.map(item -> item.values().get(field.name())).orElse(null);
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
                if (!entered.isBlank()) values.put(field.name(), entered.strip());
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
                        "reasoning configure options must be repeated --set <field>=<value>");
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

    private static void setEnabled(List<String> arguments, ReasoningConfigurationManager manager,
            boolean enabled) throws IOException {
        if (arguments.size() != 2) {
            throw new IllegalArgumentException("reasoning " + (enabled ? "enable" : "disable")
                    + " requires <provider>/<instance>");
        }
        ProviderInstanceRef ref = reference(arguments.get(1));
        manager.setEnabled(ref.provider(), ref.instance(), enabled);
        System.out.println("reasoning.state\t" + arguments.get(1) + "\t"
                + (enabled ? "enabled" : "disabled"));
    }

    private static void remove(List<String> arguments, ReasoningConfigurationManager manager)
            throws IOException {
        if (arguments.size() != 2) {
            throw new IllegalArgumentException("reasoning remove requires <provider>/<instance>");
        }
        ProviderInstanceRef ref = reference(arguments.get(1));
        manager.remove(ref.provider(), ref.instance());
        System.out.println("reasoning.removed\t" + arguments.get(1));
    }

    private static ProviderInstanceRef reference(String value) {
        int separator = value.indexOf('/');
        if (separator <= 0 || separator == value.length() - 1
                || value.indexOf('/', separator + 1) >= 0) {
            throw new IllegalArgumentException("reasoning instance reference must be <provider>/<instance>");
        }
        return new ProviderInstanceRef(value.substring(0, separator), value.substring(separator + 1));
    }

    private static List<Path> reasoningArtifactDirectories(Properties properties,
            HostEnvironment host) {
        String configured = properties.getProperty("reasoning.directory");
        return configured == null || configured.isBlank()
                ? host.reasoningDirectories()
                : List.of(Path.of(configured.strip()).toAbsolutePath().normalize());
    }

    static void usage() {
        System.err.println("reasoning commands:");
        System.err.println("  madre reasoning providers");
        System.err.println("  madre reasoning list");
        System.err.println("  madre reasoning inspect <provider>/<instance>");
        System.err.println("  madre reasoning configure <provider> <instance> [--set <field>=<value>]...");
        System.err.println("  madre reasoning enable <provider>/<instance>");
        System.err.println("  madre reasoning disable <provider>/<instance>");
        System.err.println("  madre reasoning remove <provider>/<instance>");
        System.err.println("  madre reasoning install <jar> [--replace]");
        System.err.println("  madre reasoning uninstall <provider-id> [--purge-configuration]");
    }

    private record ProviderInstanceRef(String provider, String instance) { }
}
