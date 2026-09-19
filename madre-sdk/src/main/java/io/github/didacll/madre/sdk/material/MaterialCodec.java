package io.github.didacll.madre.sdk.material;

/** Module-owned codec for one semantic payload type. */
public interface MaterialCodec<T> {
    byte[] encode(T value);
    T decode(byte[] bytes);
}
