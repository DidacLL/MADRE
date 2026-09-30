package io.github.didacll.madre.runtime;

import io.github.didacll.madre.kernel.client.EngineDescriptor;

import java.nio.file.Path;

/** Minimal local command surface for the Runtime installation base. */
public final class RuntimeMain {
    private RuntimeMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            usage();
            System.exit(2);
        }
        try (MadreRuntime runtime = new MadreRuntime(Path.of(args[0]))) {
            RuntimeInstallation installation = runtime.installation();
            switch (args[1]) {
                case "init" -> requireLength(args, 2);
                case "install" -> {
                    requireLength(args, 3);
                    System.out.println("Staged " + runtime.install(Path.of(args[2])));
                }
                case "artifacts" -> {
                    requireLength(args, 2);
                    for (String artifact : installation.artifacts()) System.out.println(artifact);
                }
                case "remove" -> {
                    requireLength(args, 3);
                    runtime.remove(args[2]);
                }
                case "modules" -> {
                    requireLength(args, 2);
                    String core = runtime.core().orElse("unassigned");
                    String unavailable = !core.equals("unassigned") && runtime.module(core).isEmpty()
                            ? " (unavailable)" : "";
                    System.out.println("CORE: " + core + unavailable);
                    for (MadreRuntime.ModuleInfo module : runtime.modules()) System.out.println(module);
                }
                case "assign-core" -> {
                    requireLength(args, 3);
                    runtime.assignCore(args[2]);
                }
                case "kernel-endpoint" -> {
                    if (args.length == 3) installation.configureKernelEndpoint(Path.of(args[2]));
                    else requireLength(args, 2);
                    System.out.println(installation.kernelEndpoint().map(Path::toString).orElse("unconfigured"));
                }
                case "engines" -> {
                    requireLength(args, 2);
                    for (EngineDescriptor engine : runtime.engines()) {
                        System.out.println(engine.engineId() + " work=" + engine.supportedWorkTypes()
                                + " placement=" + engine.placement() + " availability=" + engine.availability());
                    }
                }
                default -> {
                    usage();
                    System.exit(2);
                }
            }
        }
    }

    private static void requireLength(String[] args, int count) {
        if (args.length != count) throw new IllegalArgumentException("Expected " + count + " arguments");
    }

    private static void usage() {
        System.err.println("Usage: madre-runtime <installation-dir> "
                + "<init|install JAR|artifacts|remove JAR-NAME|modules|assign-core MODULE-ID|"
                + "kernel-endpoint [ENDPOINT]|engines>");
    }
}
