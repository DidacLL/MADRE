package io.github.didacll.madre.sdk.identity;

final class IdentityValues {
    private IdentityValues() { }

    static String requireName(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        String normalized = value.strip();
        if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException(label + " contains unsupported characters: " + value);
        }
        return normalized;
    }
}
