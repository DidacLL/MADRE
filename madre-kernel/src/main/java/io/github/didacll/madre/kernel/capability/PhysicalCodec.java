package io.github.didacll.madre.kernel.capability;

/** Stable opaque-byte codec required by durable physical work. */
public interface PhysicalCodec<T> {
    byte[] encode(T value);
    T decode(byte[] bytes);
}
