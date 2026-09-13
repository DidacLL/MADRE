package io.github.didacll.madre.app;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.interaction.BackgroundUpdate;
import io.github.didacll.madre.interaction.OwnerInteractionModule;
import io.github.didacll.madre.sdk.execution.PhysicalExecutionException;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.websearch.ResearchSource;
import io.github.didacll.madre.websearch.SearchResultSet;
import io.github.didacll.madre.websearch.WebSearchModule;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
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
        try (var input = Files.newInputStream(Path.of(arguments[0]))) {
            properties.load(input);
        }
        try (MadreApplication application = MadreApplication.start(properties)) {
            runConsole(application.interaction(), application.webSearch());
        }
    }

    static void runConsole(OwnerInteractionModule interaction, WebSearchModule webSearch)
            throws IOException {
        Object outputLock = new Object();
        ScheduledExecutorService background = Executors.newSingleThreadScheduledExecutor();
        background.scheduleWithFixedDelay(() -> printBackground(interaction, outputLock),
                250, 250, TimeUnit.MILLISECONDS);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            synchronized (outputLock) {
                System.out.println("MADRE ready — ordinary text uses fast lane; "
                        + "/standard <prompt>; /search <S1..S5> <query>; "
                        + "/deep-search <S1..S5> <query-1> || <query-2>; /exit");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String input = line.strip();
                if (input.equals("/exit") || input.equals("/quit")) break;
                if (input.startsWith("/search ")) {
                    runSingleSearch(webSearch, input.substring("/search ".length()), outputLock);
                    continue;
                }
                if (input.startsWith("/deep-search ")) {
                    runDeepSearch(webSearch, input.substring("/deep-search ".length()), outputLock);
                    continue;
                }
                boolean standard = input.startsWith("/standard ");
                String prompt = standard ? input.substring("/standard ".length()).strip() : input;
                if (prompt.isBlank()) continue;
                try {
                    Material<String> ownerPrompt = interaction.ownerPrompt(prompt, Sensitivity.S5);
                    Material<String> answer = (standard
                            ? interaction.standardPrompt(ownerPrompt)
                            : interaction.fastLane(ownerPrompt)).toCompletableFuture().join();
                    synchronized (outputLock) {
                        System.out.println("madre> " + answer.payload());
                    }
                } catch (CompletionException exception) {
                    printPhysicalFailure(exception.getCause(), outputLock);
                } catch (RuntimeException exception) {
                    printPhysicalFailure(exception, outputLock);
                }
            }
        } finally {
            background.shutdownNow();
            printBackground(interaction, outputLock);
        }
    }

    private static void runSingleSearch(WebSearchModule webSearch, String arguments,
            Object outputLock) {
        try {
            ResearchInput request = researchInput(arguments);
            Material<SearchResultSet> result = webSearch.singleSearch(
                    webSearch.searchQuery(request.query(), request.sensitivity()))
                    .toCompletableFuture().join();
            synchronized (outputLock) {
                System.out.println("search> " + result.payload().query());
                for (ResearchSource source : result.payload().sources()) {
                    System.out.println("- " + source.title() + " — " + source.url());
                    if (!source.excerpt().isBlank()) System.out.println("  " + source.excerpt());
                }
            }
        } catch (CompletionException exception) {
            printPhysicalFailure(exception.getCause(), outputLock);
        } catch (RuntimeException exception) {
            printPhysicalFailure(exception, outputLock);
        }
    }

    private static void runDeepSearch(WebSearchModule webSearch, String arguments,
            Object outputLock) {
        try {
            int separator = arguments.indexOf(" || ");
            if (separator < 0) {
                throw new IllegalArgumentException(
                        "deep search requires two queries separated by ' || '");
            }
            ResearchInput first = researchInput(arguments.substring(0, separator));
            String secondQuery = arguments.substring(separator + " || ".length()).strip();
            if (secondQuery.isBlank()) {
                throw new IllegalArgumentException("second deep-search query must not be blank");
            }
            Material<String> review = webSearch.deepSearch(
                    webSearch.searchQuery(first.query(), first.sensitivity()),
                    webSearch.searchQuery(secondQuery, first.sensitivity()))
                    .toCompletableFuture().join();
            synchronized (outputLock) {
                System.out.println("research> " + review.payload());
            }
        } catch (CompletionException exception) {
            printPhysicalFailure(exception.getCause(), outputLock);
        } catch (RuntimeException exception) {
            printPhysicalFailure(exception, outputLock);
        }
    }

    private static ResearchInput researchInput(String arguments) {
        String value = arguments.strip();
        int split = value.indexOf(' ');
        if (split < 1 || split == value.length() - 1) {
            throw new IllegalArgumentException("search requires <S1..S5> <query>");
        }
        Sensitivity sensitivity;
        try {
            sensitivity = Sensitivity.valueOf(value.substring(0, split).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("search sensitivity must be S1, S2, S3, S4, or S5",
                    exception);
        }
        String query = value.substring(split + 1).strip();
        if (query.isBlank()) throw new IllegalArgumentException("search query must not be blank");
        return new ResearchInput(sensitivity, query);
    }

    private static void printBackground(OwnerInteractionModule interaction,
            Object outputLock) {
        try {
            for (BackgroundUpdate update : interaction.collectBackground()) {
                synchronized (outputLock) {
                    if (update.visibleFollowUp().isPresent()) {
                        System.out.println("background> "
                                + update.visibleFollowUp().orElseThrow().payload());
                    } else if (update.physicalState()
                            != io.github.didacll.madre.sdk.execution.WorkState.SUCCEEDED) {
                        System.err.println("background physical failure [" + update.workId().value()
                                + "]: " + update.physicalFailure().map(Enum::name)
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
                System.err.println("operation failure: "
                        + (failure == null ? "unknown failure" : failure.getMessage()));
            }
        }
    }

    private record ResearchInput(Sensitivity sensitivity, String query) { }
}
