# Repository Governance

Author: ag
State: read

This file is stable agent procedure for repository-level engineering workflow. It does not define MADRE product architecture.

## Branches

- `main` is the protected target branch, not the normal work branch.
- Agents must not commit directly to `main`.
- All non-trivial changes must happen on topic branches.
- Branch names:
  - `agentic/<short-purpose>` for agentic work.
  - `docs/<short-purpose>` for human documentation-only work.
  - `fix/<short-purpose>` for narrow corrective patches.
  - `runtime/<short-purpose>` for implementation slices after the readiness gate.

## Pull Requests

PRs are the integration unit. Every PR must report:

- Owned files.
- Why those files own the change.
- Validation performed.
- Risks.
- Whether runtime code was added.
- Whether TeX was changed.

## Main Protection

`main` should be protected through GitHub settings with:

- Require PR before merge.
- Require status checks before merge.
- Require conversation resolution before merge.
- Disallow force pushes.
- Disallow deletions.
- Prefer linear history or squash/rebase merge.

Repository governance docs define required behavior even when GitHub settings must be configured manually by the owner.
