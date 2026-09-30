package io.github.didacll.madre.runtime;

import io.github.didacll.madre.kernel.client.EngineDescriptor;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.time.Instant;

/** Local command entry for installation, inspection and the live Runtime loop. */
public final class RuntimeMain {
    private RuntimeMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            usage();
            System.exit(2);
        }
        Path home = Path.of(args[0]);
        RuntimeInstallation installation = new RuntimeInstallation(home);
        switch (args[1]) {
            case "init" -> requireLength(args, 2);
            case "install" -> {
                requireLength(args, 3);
                System.out.println("Staged " + installation.installArtifact(Path.of(args[2])));
            }
            case "artifacts" -> {
                requireLength(args, 2);
                for (String artifact : installation.artifacts()) System.out.println(artifact);
            }
            case "remove" -> {
                requireLength(args, 3);
                installation.removeArtifact(args[2]);
            }
            case "assign-core" -> {
                requireLength(args, 3);
                installation.assignCore(args[2]);
            }
            case "kernel-endpoint" -> {
                if (args.length == 3) installation.configureKernelEndpoint(Path.of(args[2]));
                else requireLength(args, 2);
                System.out.println(installation.kernelEndpoint().map(Path::toString).orElse("unconfigured"));
            }
            case "modules" -> {
                requireLength(args, 2);
                try (MadreRuntime runtime = new MadreRuntime(home)) {
                    String core = runtime.core().orElse("unassigned");
                    System.out.println("CORE: " + core +
                            (!core.equals("unassigned") && runtime.module(core).isEmpty()
                                    ? " (unavailable)" : ""));
                    for (MadreRuntime.ModuleInfo module : runtime.modules()) System.out.println(module);
                } catch (IOException occupied) {
                    if (occupied.getMessage() == null ||
                            !occupied.getMessage().startsWith("Runtime installation is already active")) {
                        throw occupied;
                    }
                    System.out.print(Files.readString(home.resolve("runtime-modules.txt")));
                }
            }
            case "engines" -> {
                requireLength(args, 2);
                for (EngineDescriptor engine : installation.engines()) {
                    System.out.println(engine.engineId() + " work=" + engine.supportedWorkTypes()
                            + " placement=" + engine.placement()
                            + " availability=" + engine.availability());
                }
            }
            case "wakeups" -> {
                requireLength(args, 2);
                for (RuntimeWakeups.Wakeup wakeup : new RuntimeWakeups(installation.home()).pending()) {
                    System.out.println("Wakeup " + wakeup.id() + " module=" + wakeup.moduleId()
                            + " due=" + wakeup.due() + " attempts=" + wakeup.attempts()
                            + " diagnostic=" + wakeup.error());
                }
            }
            case "run-due", "serve" -> {
                requireLength(args, 2);
                try (MadreRuntime runtime = new MadreRuntime(home)) {
                    switch (args[1]) {
                        case "run-due" -> System.out.println("Delivered " + runtime.runDue(Instant.now()));
                        case "serve" -> {
                            Thread shutdown = new Thread(() -> {
                                try { runtime.close(); }
                                catch (Exception failure) { failure.printStackTrace(System.err); }
                            }, "madre-runtime-shutdown");
                            java.lang.Runtime.getRuntime().addShutdownHook(shutdown);
                            try {
                                while (!Thread.currentThread().isInterrupted()) {
                                    runtime.discover();
                                    runtime.runDue(Instant.now());
                                    Thread.sleep(1000);
                                }
                            } finally {
                                try { java.lang.Runtime.getRuntime().removeShutdownHook(shutdown); }
                                catch (IllegalStateException stopping) { /* shutdown hook is running */ }
                            }
                        }
                        default -> throw new AssertionError(args[1]);
                    }
                }
            }
            default -> {
                usage();
                System.exit(2);
            }
        }
    }

    private static void requireLength(String[] args, int count) {
        if (args.length != count) throw new IllegalArgumentException("Expected " + count + " arguments");
    }

    private static void usage() {
        System.err.println("Usage: madre-runtime <installation-dir> "
                + "<init|install JAR|artifacts|remove JAR-NAME|modules|assign-core MODULE-ID|"
                + "kernel-endpoint [ENDPOINT]|engines|wakeups|run-due|serve>");
    }
}
