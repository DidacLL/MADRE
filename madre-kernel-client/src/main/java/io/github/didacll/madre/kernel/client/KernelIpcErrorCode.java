package io.github.didacll.madre.kernel.client;

public enum KernelIpcErrorCode {
    InvalidRequest,
    NotFound,
    WorkNotTerminal,
    InternalFailure,
    ProtocolError,
    TransportFailure,
    Timeout
}
