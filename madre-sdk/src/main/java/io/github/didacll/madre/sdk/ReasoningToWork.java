package io.github.didacll.madre.sdk;

/**
 * Module-owned Operation selected by an installation to turn a semantic
 * ReasoningRequest and execution preferences into physical Work requirements.
 *
 * <p>The public call signature remains open until the preference/declaration
 * contract is designed. An implementation belongs to an ordinary Module;
 * Runtime supplies selection and execution mechanics. A Module exposes an
 * implementation through its ordinary {@link MADREModule#operations()} surface.
 */
public interface ReasoningToWork extends ModuleOperation {
}
