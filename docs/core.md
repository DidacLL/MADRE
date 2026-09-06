# CORE

CORE is MADRE's first-party application. Its current behavior is intentionally small: an interactive terminal conversation that submits ordinary chat work to a separately running MADRE runtime over the authenticated HTTP application boundary.

CORE does not start or bypass the runtime, invoke a capability directly, or own a scheduler. Its submitted work uses the stable application identity `madre-core` and therefore shares the same durable work lifecycle and local-inference admission rules as other applications.

## Run

Start the MADRE runtime normally with a configured chat capability and `MADRE_API_TOKEN`, as described in `README.md`. In another terminal, expose the same local service token and run:

```console
python -m uv run --locked madre-core --runtime-url http://127.0.0.1:8731 --capability local-chat
```

Enter a message at `you>`. Generated assistant text is printed at `core>`. CORE then prints the fast interaction decision as either `reasoning> fast` or `reasoning> deeper`. Use `/exit`, `/quit`, Ctrl+C, or end-of-input to stop.

`--token-env` defaults to `MADRE_API_TOKEN`, `--max-tokens` defaults to 256, and `--timeout` defaults to 120 seconds. The runtime URL must be a literal loopback HTTP(S) origin. CORE never prints the bearer token.

If MADRE returns work as `accepted` or `running`, CORE polls the ordinary work-inspection endpoint until the work succeeds or fails. Durable runtime/capability failures are shown as `CORE error: ...` rather than being interpreted as assistant output.

## Fast interaction responsibility

Each user turn creates exactly one ordinary CORE chat work item. CORE adds a transient system instruction asking the configured capability for a concise immediate answer and one bounded recommendation: `fast` when that response is sufficient, or `deeper` when materially better handling would require deeper multi-step reasoning, verification, research, planning, or tools.

CORE software strips that internal marker before displaying or remembering the assistant response. If the capability omits or malforms the marker, CORE preserves the useful response but conservatively reports `deeper` rather than silently assuming the fast answer is sufficient.

The recommendation is observable only. `deeper` does not schedule another job, deploy an agent, invoke a Planner, change capability selection, or claim that deeper reasoning occurred. This slice establishes the decision point before giving it execution consequences.

"Fast" describes CORE's interaction responsibility: one bounded foreground inference intended to answer the current turn promptly. It is not a wall-clock scheduling guarantee. Ordinary MADRE runtime admission may still delay the work when another application occupies the heavyweight local-inference slot.

## State

Conversation history exists only in the running CORE process. A successful assistant response, without CORE's internal reasoning marker, is appended to that in-memory history and included in the next chat-completion input. Failed turns are not appended. Restarting CORE forgets the conversation.

There is no persistent CORE session store, memory system, agent abstraction, planner, background reasoning loop, automatic escalation, capability bypass, or runtime special case in this slice.
