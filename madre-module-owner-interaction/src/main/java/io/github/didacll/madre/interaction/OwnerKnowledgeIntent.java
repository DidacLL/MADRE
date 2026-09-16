package io.github.didacll.madre.interaction;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small CORE-private parser for explicit owner knowledge controls in ordinary language. */
record OwnerKnowledgeIntent(Action action, OwnerKnowledgeKind kind, String key, String value) {
    private static final Pattern REMEMBER = Pattern.compile(
            "(?i)^remember(?:\\s+that)?\\s+(?:my\\s+)?(.+?)\\s+(?:is|=)\\s+(.+)$");
    private static final Pattern FORGET = Pattern.compile(
            "(?i)^(?:forget|remove)\\s+my\\s+(.+?)[?.]*$");
    private static final Pattern REVEAL = Pattern.compile(
            "(?i)^(?:show|tell)\\s+me\\s+my\\s+(.+?)[?.]*$");
    private static final Pattern WHAT_IS_MY = Pattern.compile(
            "(?i)^what\\s+is\\s+my\\s+(.+?)[?.]*$");
    private static final Pattern PREFERENCE = Pattern.compile("(?i)^i\\s+prefer\\s+(.+?)[.]*$");
    private static final Pattern REPLY_STYLE = Pattern.compile(
            "(?i)^please\\s+(?:keep|make)\\s+(?:your\\s+)?(?:replies|responses)\\s+(.+?)[.]*$");

    OwnerKnowledgeIntent {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(kind, "kind");
        key = Objects.requireNonNull(key, "key").strip();
        value = Objects.requireNonNull(value, "value").strip();
        if (key.isBlank()) throw new IllegalArgumentException("knowledge key must not be blank");
        if (action == Action.PUT && value.isBlank()) {
            throw new IllegalArgumentException("knowledge value must not be blank");
        }
    }

    static Optional<OwnerKnowledgeIntent> parse(String text) {
        String value = Objects.requireNonNull(text, "text").strip();
        if (value.isBlank()) return Optional.empty();
        String lowered = value.toLowerCase(Locale.ROOT);
        if (lowered.equals("what do you remember about me?")
                || lowered.equals("what do you remember about me")
                || lowered.equals("what do you know about me?")
                || lowered.equals("what do you know about me")) {
            return Optional.of(new OwnerKnowledgeIntent(Action.LIST,
                    OwnerKnowledgeKind.OWNER_FACT, "all", ""));
        }

        Matcher preference = PREFERENCE.matcher(value);
        if (preference.matches()) {
            return Optional.of(new OwnerKnowledgeIntent(Action.PUT,
                    OwnerKnowledgeKind.INTERACTION_PREFERENCE, "interaction style",
                    preference.group(1)));
        }
        Matcher replyStyle = REPLY_STYLE.matcher(value);
        if (replyStyle.matches()) {
            return Optional.of(new OwnerKnowledgeIntent(Action.PUT,
                    OwnerKnowledgeKind.INTERACTION_PREFERENCE, "interaction style",
                    replyStyle.group(1)));
        }
        Matcher remember = REMEMBER.matcher(value);
        if (remember.matches()) {
            String key = remember.group(1).strip();
            // CORE knowledge is not a credential vault. Treat explicit credential-like storage
            // attempts as a bounded removal request so the value is neither persisted nor sent
            // through ordinary reasoning, while also clearing matching legacy experiment state.
            if (credentialLike(key)) {
                return Optional.of(new OwnerKnowledgeIntent(Action.REMOVE,
                        OwnerKnowledgeKind.HIGHLY_SENSITIVE, key, ""));
            }
            Optional<OwnerKnowledgeKind> kind = classify(key);
            if (kind.isPresent()) {
                return Optional.of(new OwnerKnowledgeIntent(Action.PUT, kind.orElseThrow(), key,
                        remember.group(2).strip()));
            }
            return Optional.empty();
        }
        Matcher forget = FORGET.matcher(value);
        if (forget.matches()) {
            return Optional.of(new OwnerKnowledgeIntent(Action.REMOVE,
                    OwnerKnowledgeKind.OWNER_FACT, forget.group(1), ""));
        }
        Matcher reveal = REVEAL.matcher(value);
        if (reveal.matches()) {
            String key = reveal.group(1).strip();
            if (credentialLike(key)) {
                return Optional.of(new OwnerKnowledgeIntent(Action.REMOVE,
                        OwnerKnowledgeKind.HIGHLY_SENSITIVE, key, ""));
            }
            return Optional.of(new OwnerKnowledgeIntent(Action.REVEAL,
                    OwnerKnowledgeKind.OWNER_FACT, key, ""));
        }
        Matcher what = WHAT_IS_MY.matcher(value);
        if (what.matches()) {
            String key = what.group(1).strip();
            if (credentialLike(key)) {
                return Optional.of(new OwnerKnowledgeIntent(Action.REMOVE,
                        OwnerKnowledgeKind.HIGHLY_SENSITIVE, key, ""));
            }
            return Optional.of(new OwnerKnowledgeIntent(Action.REVEAL,
                    OwnerKnowledgeKind.OWNER_FACT, key, ""));
        }
        return Optional.empty();
    }

    private static Optional<OwnerKnowledgeKind> classify(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "private note", "confidential note", "sensitive note",
                "private reference", "sensitive reference", "confidential reference")) {
            return Optional.of(OwnerKnowledgeKind.HIGHLY_SENSITIVE);
        }
        if (containsAny(normalized, "reply", "response", "tone", "style", "verbosity",
                "language", "format", "spelling")) {
            return Optional.of(OwnerKnowledgeKind.INTERACTION_PREFERENCE);
        }
        if (containsAny(normalized, "workspace", "working directory", "project directory",
                "repository path", "repo path", "shell", "operating system", "environment",
                "machine", "host")) {
            return Optional.of(OwnerKnowledgeKind.ENVIRONMENT_FACT);
        }
        if (containsAny(normalized, "preferred name", "name", "time zone", "timezone",
                "locale", "favorite", "favourite")) {
            return Optional.of(OwnerKnowledgeKind.OWNER_FACT);
        }
        return Optional.empty();
    }

    private static boolean credentialLike(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return containsAny(normalized, "password", "passphrase", "access code", "pin",
                "private key", "recovery code", "recovery phrase", "api key", "token",
                "account number", "social security", "tax id");
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    enum Action { PUT, REMOVE, REVEAL, LIST }
}
