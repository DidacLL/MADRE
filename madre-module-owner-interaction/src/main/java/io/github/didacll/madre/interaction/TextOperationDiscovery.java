package io.github.didacll.madre.interaction;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.directory.ModuleDirectory;
import io.github.didacll.madre.sdk.directory.ReachableOperation;
import io.github.didacll.madre.sdk.material.MaterialTypeDefinition;
import io.github.didacll.madre.sdk.module.EffectProfile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CORE-private interpretation of the small portable Operation shape the shipped Agent can honestly
 * compose today: one UTF-8 text input, one UTF-8 text output, and at most one EffectProfile.
 * Structural discovery remains in the caller-bound SDK directory; this class only decides whether
 * one discovered textual contract is uniquely relevant to ordinary owner language.
 */
final class TextOperationDiscovery {
    private static final String UTF8_TEXT = "text/plain; charset=utf-8";
    private static final int MIN_SHARED_TERMS = 2;
    private static final Pattern TERM = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "application", "by", "for", "from", "in", "installed",
            "into", "is", "it", "module", "my", "of", "on", "or", "owner", "please",
            "state", "that", "the", "this", "to", "with");

    private final ModuleDirectory directory;

    TextOperationDiscovery(ModuleDirectory directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    Optional<Candidate> select(String ownerText, Sensitivity sensitivity) {
        Objects.requireNonNull(ownerText, "ownerText");
        Objects.requireNonNull(sensitivity, "sensitivity");
        Set<String> ownerTerms = terms(ownerText);
        List<ScoredCandidate> scored = new ArrayList<>();
        for (ReachableOperation reachable : directory.reachableOperations(sensitivity)) {
            candidate(reachable, sensitivity).ifPresent(candidate -> {
                String description = candidate.reachable().operation().description();
                Optional<String> action = actionTerm(description);
                if (action.isEmpty() || !ownerTerms.contains(action.orElseThrow())) return;
                int score = score(ownerTerms, terms(description));
                if (score >= MIN_SHARED_TERMS) scored.add(new ScoredCandidate(candidate, score));
            });
        }
        scored.sort(java.util.Comparator.comparingInt(ScoredCandidate::score).reversed()
                .thenComparing(value -> value.candidate().reachable().moduleId().value())
                .thenComparing(value -> value.candidate().reachable().operation().id().name()));
        if (scored.isEmpty()) return Optional.empty();
        if (scored.size() > 1 && scored.get(0).score() == scored.get(1).score()) {
            return Optional.empty();
        }
        return Optional.of(scored.getFirst().candidate());
    }

    private static Optional<Candidate> candidate(ReachableOperation reachable,
            Sensitivity sensitivity) {
        var operation = reachable.operation();
        if (operation.acceptedMaterial().size() != 1
                || operation.producedMaterial().size() != 1
                || operation.effectProfiles().size() > 1) {
            return Optional.empty();
        }
        var inputEntry = operation.acceptedMaterial().entrySet().iterator().next();
        var outputEntry = operation.producedMaterial().entrySet().iterator().next();
        if (!sensitivity.canReach(inputEntry.getValue())) return Optional.empty();
        if (!inputEntry.getKey().moduleId().equals(reachable.moduleId())
                || !outputEntry.getKey().moduleId().equals(reachable.moduleId())) {
            return Optional.empty();
        }
        MaterialTypeDefinition input = reachable.materialTypes().get(inputEntry.getKey());
        MaterialTypeDefinition output = reachable.materialTypes().get(outputEntry.getKey());
        if (input == null || output == null || !isUtf8Text(input) || !isUtf8Text(output)) {
            return Optional.empty();
        }
        Optional<EffectProfile> effect = operation.effectProfiles().values().stream().findFirst();
        return Optional.of(new Candidate(reachable, input, output, effect));
    }

    private static boolean isUtf8Text(MaterialTypeDefinition definition) {
        return UTF8_TEXT.equalsIgnoreCase(definition.contentType().strip());
    }

    private static int score(Set<String> left, Set<String> right) {
        Set<String> shared = new HashSet<>(left);
        shared.retainAll(right);
        return shared.size();
    }

    private static Optional<String> actionTerm(String value) {
        var matcher = TERM.matcher(value.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String term = matcher.group();
            if (term.length() >= 3 && !STOP_WORDS.contains(term)) return Optional.of(term);
        }
        return Optional.empty();
    }

    private static Set<String> terms(String value) {
        Set<String> result = new HashSet<>();
        var matcher = TERM.matcher(value.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String term = matcher.group();
            if (term.length() >= 3 && !STOP_WORDS.contains(term)) result.add(term);
        }
        return Set.copyOf(result);
    }

    record Candidate(ReachableOperation reachable, MaterialTypeDefinition inputType,
            MaterialTypeDefinition outputType, Optional<EffectProfile> effectProfile) {
        Candidate {
            Objects.requireNonNull(reachable, "reachable");
            Objects.requireNonNull(inputType, "inputType");
            Objects.requireNonNull(outputType, "outputType");
            Objects.requireNonNull(effectProfile, "effectProfile");
        }
    }

    private record ScoredCandidate(Candidate candidate, int score) {}
}
