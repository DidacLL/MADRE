package io.github.didacll.madre.app;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.core.BackgroundUpdate;
import io.github.didacll.madre.core.CoreModule;
import io.github.didacll.madre.sdk.execution.PhysicalExecutionException;
import io.github.didacll.madre.sdk.material.Material;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Replaceable local console presentation for the shipped MADRE installation. */
public final class MadreMain {
    private MadreMain() { }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            System.err.println("usage: madre <path-to-madre.properties>");
            System.exit(2);
        }
        Properties properties = new Properties();
        try (var input = Files.newInputStream(Path.of(arguments[0]))) { properties.load(input); }
        try (MadreApplication application = MadreApplication.start(properties)) {
            runConsole(application.core());
        }
    }

    static void runConsole(CoreModule core) throws IOException {
        Object outputLock = new Object();
        ScheduledExecutorService background = Executors.newSingleThreadScheduledExecutor();
        background.scheduleWithFixedDelay(() -> printBackground(core, outputLock), 250, 250,
                TimeUnit.MILLISECONDS);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            synchronized (outputLock) {
                System.out.println("MADRE CORE ready — ordinary text uses fast lane; /standard <prompt>; /exit");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String input = line.strip();
                if (input.equals("/exit") || input.equals("/quit")) break;
                boolean standard = input.startsWith("/standard ");
                String prompt = standard ? input.substring("/standard ".length()).strip() : input;
                if (prompt.isBlank()) continue;
                try {
                    Material<String> ownerPrompt = core.ownerPrompt(prompt, Sensitivity.S5);
                    Material<String> answer = (standard ? core.standardPrompt(ownerPrompt)
                            : core.fastLane(ownerPrompt)).toCompletableFuture().join();
                    synchronized (outputLock) { System.out.println("core> " + answer.payload()); }
                } catch (CompletionException exception) {
                    printPhysicalFailure(exception.getCause(), outputLock);
                } catch (RuntimeException exception) {
                    printPhysicalFailure(exception, outputLock);
                }
            }
        } finally {
            background.shutdownNow();
            printBackground(core, outputLock);
        }
    }

    private static void printBackground(CoreModule core, Object outputLock) {
        try {
            for (BackgroundUpdate update : core.collectBackground()) {
                synchronized (outputLock) {
                    if (update.visibleFollowUp().isPresent()) {
                        System.out.println("background> " + update.visibleFollowUp().orElseThrow().payload());
                    } else if (update.physicalState() != io.github.didacll.madre.sdk.execution.WorkState.SUCCEEDED) {
                        System.err.println("background physical failure [" + update.workId().value() + "]: "
                                + update.physicalFailure().map(Enum::name)
                                        .orElse(update.physicalState().name()));
                    }
                }
            }
        } catch (RuntimeException exception) {
            synchronized (outputLock) {
                System.err.println("background collection failure: " + exception.getMessage());
            }
        }
    }

    private static void printPhysicalFailure(Throwable failure, Object outputLock) {
        Throwable current = failure;
        while (current != null && !(current instanceof PhysicalExecutionException)
                && current.getCause() != current) current = current.getCause();
        synchronized (outputLock) {
            if (current instanceof PhysicalExecutionException physical) {
                System.err.println("physical failure [" + physical.category() + "]: "
                        + physical.getMessage());
            } else {
                System.err.println("operation failure: " + failure.getMessage());
            }
        }
    }
}
