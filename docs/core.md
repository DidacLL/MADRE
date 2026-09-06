# CORE

CORE is MADRE's first-party application. Its current behavior is intentionally small: an interactive terminal conversation that submits ordinary chat work to a separately running MADRE runtime over the authenticated HTTP application boundary.

CORE does not start or bypass the runtime, invoke a capability directly, or own a scheduler. Its submitted work uses the stable application identity `madre-core` and therefore shares the same durable work lifecycle and local-inference admission rules as other applications.

## Run

Start the MADRE runtime normally with a configured chat capability and `MADRE_API_TOKEN`, as described in `README.md`. In another terminal, expose the same local service token and run:

```console
python -m uv run --locked madre-core --runtime-url http://127.0.0.1:8731 --capability local-chat
```

Enter a message at `you>`. Generated assistant text is printed at `core>`. CORE then prints the fast interaction decision as either `reasoning> fast` or `reasoning> deeper`. When the latest turn recommends `deeper`, CORE also shows `deeper> /deeper`; entering `/deeper` explicitly requests one stronger follow-up. Use `/exit`, `/quit`, Ctrl+C, or end-of-input to stop.

`--token-env` defaults to `MADRE_API_TOKEN`, `--max-tokens` defaults to 256, `--deeper-max-tokens` defaults to 768, and `--timeout` defaults to 120 seconds. The deeper token budget must exceed the fast token budget. The runtime URL must be a literal loopback HTTP(S) origin. CORE never prints the bearer token.

If MADRE returns work as `accepted` or `running`, CORE polls the ordinary work-inspection endpoint until the work succeeds or fails. Durable runtime/capability failures are shown as `CORE error: ...` rather than being interpreted as assistant output.

## Fast interaction responsibility

Each ordinary user turn creates exactly one CORE chat work item. CORE adds a transient system instruction asking the configured capability for a concise immediate answer and one bounded recommendation: `fast` when that response is sufficient, or `deeper` when materially better handling would require deeper multi-step reasoning, verification, research, planning, or tools.

CORE software strips that internal marker before displaying or remembering the assistant response. If the capability omits or malforms the marker, CORE preserves the useful response but conservatively reports `deeper` rather than silently assuming the fast answer is sufficient.

"Fast" describes CORE's interaction responsibility: one bounded foreground inference intended to answer the current turn promptly. It is not a wall-clock scheduling guarantee. Ordinary MADRE runtime admission may still delay the work when another application occupies the heavyweight local-inference slot.

## User-controlled deeper follow-up

A `deeper` recommendation has exactly one execution consequence, and only when the user explicitly enters `/deeper`. CORE then submits one second ordinary MADRE work item through the same authenticated HTTP boundary and stable `madre-core` application identity.

That work uses the configured chat capability with the current process-local conversation, including the latest fast answer as a draft. A transient deeper-follow-up instruction asks for a materially more thorough replacement answer and uses the larger `--deeper-max-tokens` budget. This is stronger reasoning intent within the currently available chat capability; it is not a new runtime work type or capability class.

A successful deeper answer replaces the fast draft in CORE's process-local conversation history so later turns see one authoritative conversational answer rather than both drafts. The opportunity is then consumed. Sending a new ordinary user message instead abandons the previous opportunity. If the deeper work fails, the fast answer remains in history and the opportunity remains available for an explicit retry.

CORE never escalates automatically. `/deeper` is rejected unless the latest completed fast turn recommended deeper reasoning. The command does not deploy an agent, invoke a Planner, create a workflow, select another capability, or start background execution.

## Local product acceptance

The deterministic test suite proves the software protocol and runtime boundaries. The current small local model is probabilistic, so whether it recommends `fast` or `deeper` for a particular prompt—and whether the deeper answer is actually better—must be evaluated with the real model. This manual acceptance is product evidence, not routine developer QA.

The acceptance helper deliberately avoids shell-embedded Python and SQL so the same commands work from Windows PowerShell without native-command quoting ambiguity.

Run the following from the repository root.

First update, bootstrap, and prepare a clean dedicated acceptance runtime:

```powershell
git switch main
git pull --ff-only
python -m uv sync --locked
python tools/core-acceptance.py prepare
./tools/install-smoke-model.ps1
```

`prepare` writes `madre.acceptance.local.toml` from `madre.example.toml`, sets `data_dir = "./dev/core-acceptance"`, and removes any previous `dev/core-acceptance` data. Both paths are local/ignored inputs.

Terminal 1 — start the real local model:

```powershell
$fixture = Join-Path $env:LOCALAPPDATA 'MADRE\inference'
& "$fixture\llama-b10809\llama-server.exe" `
    -m "$fixture\qwen2.5-0.5b-instruct-q4_k_m.gguf" `
    --host 127.0.0.1 --port 8080 --alias madre-smoke -c 2048 -ngl 0 -t 4
```

Terminal 2 — generate one local service token, keep it private, and start MADRE:

```powershell
$token = python -c "import secrets; print(secrets.token_urlsafe(32))"
$env:MADRE_API_TOKEN = $token
$token
python -m uv run --locked madre --config madre.acceptance.local.toml serve
```

The small token-generation command contains no nested quoting and is safe in Windows PowerShell. Copy the printed token locally into Terminal 3. Do not paste it into an issue, commit, chat, or test report.

Terminal 3 — start CORE with the same token:

```powershell
$env:MADRE_API_TOKEN = '<paste the token printed in Terminal 2>'
python -m uv run --locked madre-core `
    --runtime-url http://127.0.0.1:8731 `
    --capability local-chat
```

At `you>`, try an ordinary request. You should receive non-empty `core>` output and then either `reasoning> fast` or `reasoning> deeper`. The exact recommendation is model output, not a deterministic acceptance criterion.

Before testing explicit escalation, open Terminal 4 and record the current durable CORE work status:

```powershell
python tools/core-acceptance.py status
```

The helper prints `count=<N>` plus the latest work ID, status, and `latest_max_tokens` when any CORE work exists. Treat the current count as `N`.

Back in Terminal 3, give CORE a request that genuinely benefits from analysis, for example:

```text
Design a fault-tolerant migration plan for a stateful service with zero data loss. Compare at least two strategies, identify failure modes, and justify the safer choice.
```

If CORE reports `reasoning> deeper`, do not enter `/deeper` yet. In Terminal 4 run:

```powershell
python tools/core-acceptance.py status
```

The count should now be `N + 1`, the latest work should be `succeeded`, and `latest_max_tokens=256`. This is observable proof that the complex turn created exactly one ordinary work item and that the `deeper` recommendation did not escalate automatically.

Back in Terminal 3, enter:

```text
/deeper
```

CORE should print a non-empty `core(deeper)>` response. Run the status helper again:

```powershell
python tools/core-acceptance.py status
```

The count should now be `N + 2`, the latest work should be `succeeded`, and `latest_max_tokens=768`. This proves that the explicit user action created exactly one second ordinary runtime work item with the stronger budget rather than a hidden CORE execution path.

You can then ask a normal follow-up question about the deeper answer. Because a successful deeper result replaces the fast draft in process-local history, the next turn should be conditioned on the deeper answer rather than both drafts. This is qualitative model behavior, so record surprising behavior rather than treating exact wording as a pass/fail assertion.

If the model reports `fast` for the complex request, try other genuinely multi-step requests. Before each attempt, run the status helper and treat its count as the new `N`. Repeatedly recommending `fast` for clearly complex work is itself useful product evidence about the classifier prompt/model combination; do not manufacture a `deeper` result merely to make the acceptance look successful.

Optional failure evidence: after a successful turn, stop the llama.cpp server in Terminal 1 and submit another CORE message. CORE should report a durable runtime failure such as `CORE error: MADRE work failed [connection]: ...`; `python tools/core-acceptance.py status` should show the additional failed CORE work item. Restart the model server before continuing.

This acceptance proves:

- real `User → CORE → MADRE HTTP API → durable runtime → local capability` execution;
- observable fast/deeper recommendation from the real configured model;
- no automatic execution consequence from a `deeper` recommendation;
- exactly one additional ordinary durable work item when the user enters `/deeper`;
- the larger deeper token budget reaches the existing capability path;
- real generated output is used for both stages;
- runtime/capability failures remain explicit rather than being turned into assistant text.

It does not prove that the small smoke model classifies reasoning depth well or consistently improves its answer after `/deeper`. Those are the product questions this owner-side use is intended to expose.

## State

Conversation history exists only in the running CORE process. A successful assistant response, without CORE's internal reasoning marker, is appended to that in-memory history and included in the next chat-completion input. Failed turns are not appended. Restarting CORE forgets the conversation.

There is no persistent CORE session store, memory system, agent abstraction, planner, background reasoning loop, automatic escalation, capability bypass, or runtime special case in this slice.
