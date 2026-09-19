package io.github.didacll.madre.sdk.identity;

/** Stable identity of an owner-installed Module. */
public record ModuleId(String value) {
    public ModuleId { value = IdentityValues.requireName(value, "module identity"); }
    @Override public String toString() { return value; }
}
