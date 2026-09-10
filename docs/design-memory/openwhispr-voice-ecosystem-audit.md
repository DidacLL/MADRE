# OpenWhispr ecosystem/opportunity audit for MADRE

Status: **EXTERNAL / AUDIT — non-authoritative design memory**  
Reviewed: **2026-09-10**  
OpenWhispr snapshot reviewed: `OpenWhispr/openwhispr@a2c76ef9ce2f121f5e1bd32af8a7530c8f86aff7`  
MADRE branch snapshot when recorded: `architecture/modular-agentic-clean-slate@ad5a50fcac1f3286a8e8f54b821774392f415d0c`

This document preserves external ecosystem evidence for future MADRE voice/audio work. It is deliberately **not** product authority. It does not adopt OpenWhispr, freeze a voice API, make OpenWhispr a Module, or change the `Module` / `SDK` / `Kernel` / `Capability` responsibility split. Any future architectural change must be reconciled into `MADRE.md` and the appropriate focused architecture document.

Mutable implementation details should be rechecked against the pinned OpenWhispr commit or current upstream state before implementation.

## Executive conclusion

OpenWhispr is most useful to MADRE as a **reference implementation and failure corpus**, not as an architectural or transitive dependency.

OpenWhispr is a complete Electron desktop product. Its repository combines interaction UX, platform integration, real-time audio transport, audio preprocessing, model/runtime lifecycle, local and remote speech recognition, speaker processing, notes/meetings/agents, storage/search, cloud integrations and application-specific automation. Its build separately compiles or downloads native helpers and runtimes including whisper.cpp, sherpa-onnx, FFmpeg-related functionality, llama.cpp, Qdrant, ONNX Runtime assets, diarization/VAD models and per-OS C/Swift helpers.

That decomposition supports MADRE's current ownership model:

```text
Module
    owns meaning, domain behavior and UX

MADRE SDK
    exposes reusable provider-independent typed computation

Kernel
    selects and coordinates physical execution mechanisms

Capability
    one concrete available physical computation mechanism
    with known properties
```

The strongest general lesson is not to create a generic "voice provider". Audio capture, VAD, ASR, TTS, playback, system-audio capture and diarization are independently useful computations and may be satisfied by different physical mechanisms.

The second major lesson is that Capability properties must be truthful at runtime. "Installed", "configured", "available", "local", "selected" and "actually executing" are not equivalent states. OpenWhispr's native-helper, model-asset and fallback history shows that this distinction matters for latency, privacy, reliability and resource planning.

## 1. Actual OpenWhispr layers

OpenWhispr's own technical reference describes an Electron 41 desktop application with React, a privileged main process, a restricted preload bridge, local SQLite state, native helpers, external inference binaries and an isolated ONNX worker.

A typical dictation path is conceptually:

```text
hotkey / UI
    -> MediaRecorder / capture
    -> Blob / PCM / ArrayBuffer
    -> Electron IPC
    -> temporary file or streaming transport
    -> speech runtime
    -> text
    -> optional cleanup
    -> clipboard / application insertion
```

Newer sherpa-onnx online paths use persistent streaming, partial results, explicit stream flush/finalization and fallback to full-recording decoding when the final streaming result is not trustworthy.

| Layer | OpenWhispr content | MADRE interpretation |
| --- | --- | --- |
| Product UX | overlay, control panel, settings, dictation interaction, auto-paste, meetings, notes, agent UI | mostly `Module` |
| Desktop integration | global hotkeys, keyboard hooks, clipboard/paste, mic-use detection, system-audio capture, OS permissions | Module UX plus selected reusable SDK/platform helpers |
| Audio transport | buffering, PCM streaming, file conversion, temporary audio lifecycle | SDK helper or Capability-adapter implementation |
| Audio preprocessing | VAD, AEC, channel handling | provider-independent computations with concrete Capability mechanisms |
| ASR execution | whisper.cpp, sherpa-onnx models, remote APIs | `Capability adapter` |
| Speaker processing | segmentation, embeddings, clustering, fingerprints | physical capabilities; Module owns semantic identity |
| Auxiliary inference | ONNX embeddings, llama.cpp cleanup/agents | separate capabilities; not intrinsically voice |
| Product data | SQLite history, notes, search, Qdrant, sync | OpenWhispr product functionality |
| Product integrations | MCP, REST, calendars, auth, cloud-provider settings | application/product concerns |

### What OpenWhispr implements itself

Its distinctive engineering is largely orchestration and platform plumbing:

- Electron interaction flows and settings;
- Windows native helpers for keyboard hooks, microphone-session monitoring and WASAPI process-loopback audio;
- macOS CoreAudio/Swift helpers for microphone activity, system audio and special keys;
- Linux compositor/desktop integration;
- cross-platform paste/hotkey fallbacks;
- audio capture lifecycle and IPC;
- model/runnable download and cache orchestration;
- whisper.cpp and sherpa-onnx process/server lifecycle;
- streaming partial/final result assembly and fallback;
- meeting detection and product-level speaker semantics;
- provider routing and self-hosted compatibility shims.

### What it delegates

Most relevant physical algorithms/runtimes are upstream projects:

- Whisper inference -> `whisper.cpp`;
- Parakeet/Nemotron and other ONNX speech models -> `sherpa-onnx`;
- general ONNX inference -> Microsoft ONNX Runtime;
- media conversion -> FFmpeg;
- echo cancellation -> WebRTC-derived AEC;
- local LLM behavior -> llama.cpp;
- vector search -> Qdrant;
- cloud ASR -> external APIs.

For MADRE, that usually makes the upstream runtime the cleaner dependency than OpenWhispr itself.

## 2. Voice UX and device plumbing

| Concern | Evidence from OpenWhispr | Likely MADRE owner | Audit conclusion |
| --- | --- | --- | --- |
| Microphone access | browser/Electron capture plus native mic monitoring | SDK + Capability adapter | reusable computation; do not bind to ASR |
| Device enumeration | browser/OS audio stacks | SDK | provider-independent device/resource concept |
| Push-to-talk | native Windows hook; restricted semantics under some Wayland paths | Module | interaction behavior, not speech recognition |
| Global hotkeys | Electron plus OS/compositor-specific mechanisms | Module | keep outside generic speech API |
| Audio buffering | batch blobs and streaming PCM | SDK | reusable data/lifecycle concern |
| Streaming | partial results and explicit finalization | SDK + Kernel | genuine cross-provider execution concern |
| VAD | separate from ASR in several paths | SDK + Capability adapter | distinct physical computation |
| Cancellation/interruption | stream termination/finalization materially affects result state | Kernel | execution lifecycle |
| Audio playback | separate from recognition | SDK + Capability adapter | independent computation/resource |
| System audio | WASAPI/CoreAudio/PipeWire-style platform mechanisms | SDK + Capability adapter | difficult, useful, but defer until demanded |
| Temporary audio | mechanism-specific intermediate lifecycle | Capability adapter | should normally stay internal |
| Permissions | microphone, accessibility, screen/system audio vary by OS | adapter reports state; Module owns UX | do not bake permission dialogs into speech abstraction |
| Auto-paste | extensive native/application fallbacks | Module | explicitly product UX, not generic voice |

A `VoiceDictationProvider` abstraction would incorrectly conflate at least four responsibilities:

```text
interaction gesture
+ audio acquisition
+ speech recognition
+ destination/application behavior
```

MADRE should instead let a Module decide why and when it wants audio-derived text while Kernel remains responsible for choosing the physical mechanisms.

## 3. Difficult cross-platform problems worth learning from

### System/process audio capture

OpenWhispr uses genuinely different mechanisms by platform. Its Windows helper uses WASAPI process-loopback concepts and can exclude its own process tree. macOS uses CoreAudio process/system-audio techniques. Linux depends on PipeWire/portal/compositor behavior.

This is evidence for separate concrete audio-capture Capabilities, not for a universal virtual-audio subsystem.

### Event-driven device monitoring

OpenWhispr moved microphone/meeting detection toward CoreAudio, WASAPI and Linux audio events, retaining polling only as fallback. This is the right implementation principle for MADRE where platform APIs provide events:

> Prefer an event-producing physical mechanism; expose polling only as a degraded fallback.

### Global hotkeys are not portable speech primitives

OpenWhispr requires different handling for Windows, macOS, GNOME/KDE/Hyprland and other Wayland conditions. Some environments cannot provide equivalent key-down/key-up semantics at all.

Therefore "hold this key to speak" remains Module/interaction behavior even when reusable platform helpers are available.

## 4. Physical Capability opportunity matrix

### Speech-to-text

| Mechanism | Local/offline character | Resource character | Streaming | Integration shape | MADRE value |
| --- | --- | --- | --- | --- | --- |
| Windows installed/native recognition | depends on API/mode; some grammar recognition can be local while dictation paths may be online | OS-owned assets | API-dependent | Windows API | very low distribution cost; strong constrained-machine option |
| Apple Speech | on-device where supported, otherwise service-backed behavior may apply | OS-owned | partial-result support | Apple framework | strong low-dependency option on supported devices |
| PocketSphinx | local | very small relative to modern neural ASR | supports continuous/grammar/keyphrase use | C library | useful for constrained command grammars; evidence that older mechanisms still matter |
| Vosk | local | small models around tens of MB; materially smaller than many modern ASR models | yes | C API/bindings | mature lightweight offline option |
| whisper.cpp | local | model-dependent from small to multi-GB; CPU/GPU/NPU options | possible through application/runtime patterns | C/C++ library/process/server | strong portable general-purpose local ASR |
| sherpa-onnx + Parakeet/Nemotron/etc. | local | model-dependent, often hundreds of MB | both streaming and non-streaming | native APIs/bindings/server | broad platform support and many speech computations |
| remote ASR | remote | minimal local model residency | often strong streaming | HTTPS/WebSocket/etc. | low local resource use but privacy/network/cost constraints |
| "OpenWhispr local" | inherits underlying runtime | adds product stack | underlying-runtime dependent | whole desktop application | not a distinct physical Capability |

The last row is important: MADRE should identify `whisper.cpp + model/config`, `sherpa-onnx + model/config`, a Windows recognizer, Vosk or PocketSphinx as concrete mechanisms. "OpenWhispr local" adds orchestration, not a new ASR mechanism.

### Why legacy mechanisms matter

A machine that only needs commands such as:

```text
stop
continue
cancel
read that
yes
no
```

may rationally select a compact grammar/keyphrase recognizer instead of loading a hundreds-of-MB neural model. MADRE's Capability selection exists precisely to preserve this kind of choice.

### Text-to-speech

Representative mechanisms include:

| Mechanism | Character | MADRE relevance |
| --- | --- | --- |
| Windows speech synthesis / installed voices | OS-managed, negligible MADRE model distribution | high-value cheap default on Windows |
| Apple `AVSpeechSynthesizer` | OS-managed | equivalent low-dependency Apple path |
| eSpeak NG | tiny CPU-oriented implementation, many languages, less natural output | excellent constrained-machine fallback where licensing fits |
| sherpa-onnx TTS | local neural model execution | useful if sherpa is already present; still a separate Capability |
| Piper | local neural TTS | quality-oriented option, but current project/licensing must be reviewed carefully |
| remote TTS | service-backed | useful when quality outweighs privacy/cost/network constraints |

Piper also provides a maintenance warning: the original project was archived and development moved to a different repository/license regime. Capability asset/runtime provenance must therefore be recorded for exact versions rather than inferred from a familiar project name.

### VAD

OpenWhispr's history shows that VAD materially changes cost and recognition behavior, including silence handling. VAD should not permanently belong to whichever ASR provider happens to be selected.

Plausible mechanisms include WebRTC VAD, Silero VAD, sherpa-onnx VAD and runtime-specific VAD support. A tiny independent VAD feeding another ASR engine may be the correct selection on some machines.

## 5. Dependency/integration assessment

| Component | License/language shape | Integration | Dependency assessment |
| --- | --- | --- | --- |
| OpenWhispr whole application | MIT top-level; TS/JS + C/Swift with many native/runtime dependencies | desktop process | **avoid for generic MADRE voice**; imports unrelated application architecture |
| OpenWhispr native helpers | C/Swift inside MIT repository | small subprocesses | useful source/reference; extract or reimplement narrowly |
| whisper.cpp | MIT; C/C++ | library, executable/server, bindings | strong direct Capability mechanism; model dominates footprint |
| sherpa-onnx | Apache-2.0 runtime; C++ with many bindings | library/server | strong direct speech substrate; model licenses remain independent |
| ONNX Runtime | MIT; native runtime | library/bindings | reasonable shared substrate when several ONNX capabilities justify it |
| FFmpeg | license/build varies | process/library | powerful but broad; avoid requiring it for simple canonical PCM capture |
| miniaudio | public-domain/MIT-0 style; C | single-file embedded library | attractive minimal capture/playback candidate |
| CPAL | Apache-2.0; Rust | Rust library | strong alternative if MADRE native helpers use Rust |
| Vosk | Apache-2.0 API/runtime ecosystem | library/bindings | attractive low-resource ASR option |
| PocketSphinx | permissive/BSD-style | C library | compact mature mechanism for command/grammar niches |
| Silero VAD | MIT model/reference project | ONNX or PyTorch | useful model; avoid dragging Python/Torch solely for VAD when ONNX/direct paths suffice |
| eSpeak NG | GPL-family | library/process/SAPI | excellent footprint; licensing needs deliberate review |
| OS-native speech/audio | platform component | direct OS API | lowest distribution cost; availability/locality must be probed |

Model weights and optional integrations require separate license/provenance checks. A permissively licensed runtime does not imply that every bundled model, codec or optional helper has the same license.

## 6. Reliability and complexity warnings

### Capture success is not valid-audio evidence

OpenWhispr has encountered states where recording lifecycle/UI success did not imply meaningful audio frames. MADRE should treat capture output validity as an observable result, not infer it from "recording started/stopped" state.

### Capability availability must be executable, not declarative

Native binaries can be wrong-architecture, required assets can be missing, permissions can disappear, devices can be unavailable, and a preferred helper can fail while a fallback executes.

A useful lifecycle vocabulary is conceptually:

```text
installed
available
temporarily unavailable
degraded
unsupported on this architecture
missing asset
failed self-test
```

The exact public representation remains an architecture decision; the audit only establishes that static manifest presence is insufficient.

### Streaming material has lifecycle states

Partial, final, cancelled, timed-out, truncated and failed material are not interchangeable. OpenWhispr's streaming implementation already needs these distinctions internally.

MADRE should ensure existing execution/material contracts can represent the distinctions required by real-time audio rather than inventing an audio-only execution model.

### Fallback must be visible

Native -> browser, VAD -> no VAD, GPU -> CPU and local -> remote substitutions can alter privacy, latency, quality and resource use. A Capability adapter should not silently change these semantics without Kernel-visible evidence.

### Product convenience features create disproportionate platform complexity

Automatic paste/global hotkeys require accessibility privileges, platform tools, compositor-specific behavior, clipboard restoration and race handling. This is strong evidence to keep those concerns out of generic voice SDK contracts.

## 7. Adopt

### Direct upstream speech runtimes

**Evidence:** OpenWhispr itself delegates core local recognition to whisper.cpp and sherpa-onnx rather than implementing ASR algorithms. Both upstreams expose reusable integration surfaces.

**Why it matters:** MADRE can obtain local speech computation without importing OpenWhispr's desktop product.

**Owner:** `Capability adapter`.

**Architecture impact:** none; supplies additional physical implementations.

### OS-native speech/synthesis where useful

**Evidence:** Windows and Apple expose native speech-recognition/synthesis facilities with different locality and language characteristics.

**Why it matters:** already-installed mechanisms may be the cheapest and most appropriate option on constrained machines.

**Owner:** `Capability adapter`.

**Architecture impact:** none; validates provider-neutral selection.

### Established minimal audio-I/O libraries

**Evidence:** miniaudio and CPAL provide cross-platform capture/playback without importing a complete desktop product.

**Why it matters:** MADRE should not recreate commodity audio device plumbing unless a concrete requirement exceeds existing libraries.

**Owner:** reusable `SDK` helper with platform-specific implementation beneath it.

**Architecture impact:** none unless current SDK contracts cannot represent audio devices/streams at all.

## 8. Adapt

### OpenWhispr native helpers as reference implementations

Study the narrow Windows/macOS/Linux helpers for system audio, microphone activity and key/platform behavior. Reuse source only where licensing and coupling are clean; otherwise reproduce the OS technique behind a MADRE-owned adapter.

**Owner:** `Capability adapter` or platform helper.

**Architecture impact:** implementation only.

### Streaming commit/finalization pattern

OpenWhispr's online sherpa path demonstrates a useful pattern: consume partials, flush at stop, wait for final progress, detect truncation, and fall back to a complete decode when finalization is unreliable.

**Owner:** `SDK` for observable stream semantics; `Kernel` for lifecycle/cancellation.

**Architecture impact:** strengthen existing contracts only if they cannot express this behavior.

### Process isolation for crash-prone native inference

OpenWhispr isolates ONNX execution from the Electron main process and can respawn it after native failure.

**Owner:** `Kernel` execution strategy.

**Architecture impact:** implementation policy, not a universal requirement that every Capability be out-of-process.

### Asset acquisition and verification

OpenWhispr has explicit build/runtime flows for downloading binaries and model assets. MADRE can generalize the minimal useful facts:

```text
identity/version
source
checksum
license/provenance
platform/architecture compatibility
installed state
validation state
```

**Owner:** `Capability adapter`, coordinated/observed by `Kernel`.

**Architecture impact:** only if current Capability material cannot associate required executable/model assets.

## 9. Generalize

These findings may justify future refinement of existing MADRE contracts, but do not independently change architecture.

### Execution locality is a per-mechanism/per-mode fact

"Native" or "installed" does not guarantee offline execution. Windows has native speech modes with different locality; Apple explicitly exposes on-device support.

**Owner:** `SDK` descriptor + `Kernel` selection.

**Potential change:** refine Capability properties rather than create a new subsystem.

### Runtime-observed availability matters

A Capability may be installed yet unusable because of architecture, assets, permissions, device state or process health.

**Owner:** `Kernel` / `Capability adapter`.

**Potential change:** allow runtime availability/self-test/degradation evidence to participate in selection.

### Streaming semantics need explicit representation

Real-time speech may produce:

```text
partial
final
cancelled
timed out
truncated
failed
```

**Owner:** `SDK`, coordinated by `Kernel`.

**Potential change:** strengthen generic execution/material semantics if necessary; do not build a parallel voice runtime.

### Audio devices are resources, not providers

Microphone capture, system audio, VAD, recognition, diarization, playback and TTS can be independently selected.

**Owner:** `SDK` / `Kernel`.

**Potential change:** none if existing resource/Capability composition already supports this.

### Modality-specific facts are justified; a new provider hierarchy is not

Useful speech/audio facts may include accepted sample formats, sample rates/channels, batch/streaming support, partial-result support, languages, language detection, endpointing/VAD, local/remote execution, accelerator requirements, model residency, warm-up cost and required permissions.

**Owner:** `SDK` descriptors.

**Potential change:** extensible computation-specific descriptors, not `VoiceProvider` architecture.

### Mechanism composition remains Kernel work

A valid execution may be:

```text
capture A -> VAD B -> ASR C -> diarization D
```

or remote ASR plus local speaker processing. SDK should expose what computation is needed; Kernel remains the selector/coordinator.

**Owner:** `Kernel`.

**Potential change:** none; evidence against duplicated SDK-side routing.

## 10. Avoid

### OpenWhispr as a generic MADRE dependency

It would bring Electron/product UX, storage/search, agent/meeting features and a large native/runtime dependency closure into functionality MADRE can obtain from narrower upstream mechanisms.

**Owner:** architecture boundary.

**Change:** none; preserve separation.

### OpenWhispr as a Module merely because it is an application

OpenWhispr's notes, meetings, agent behavior and cloud sync are its product semantics. They should not become MADRE domain architecture accidentally.

**Owner:** `Module`.

**Change:** none.

### OpenAI audio wire format as the MADRE SDK contract

OpenWhispr's own self-hosted shim is evidence that provider-specific HTTP shapes force translation layers. MADRE should expose the requested computation and requirements, not a provider's multipart request schema.

**Owner:** `SDK`.

**Change:** preserve provider neutrality.

### Hotkey/auto-paste semantics in generic voice APIs

They are desktop/application interaction behaviors with substantial platform-specific failure modes.

**Owner:** `Module`.

**Change:** none.

### Silent mechanism substitution

Local -> remote, native -> browser fallback, VAD -> no VAD and GPU -> CPU can materially change security/resource semantics.

**Owner:** `Kernel`.

**Change:** fallback should remain observable.

### Bundling every speech runtime by default

MADRE does not need whisper.cpp + sherpa + ONNX + every model + FFmpeg merely to support voice. Capabilities should remain optional physical mechanisms.

**Owner:** `Capability adapter` / packaging.

**Change:** implementation discipline only.

## 11. Investigate later

### System/process audio capture

Useful for meeting/system-audio Modules but substantially harder and more privacy-sensitive than microphone capture. Windows WASAPI process loopback, Apple CoreAudio taps and Linux PipeWire/portal paths should remain separate adapters until demanded.

**Owner:** `Capability adapter`.

### Echo cancellation

Relevant for duplex/meeting audio, not required for basic microphone dictation.

**Owner:** `Capability adapter`.

### Speaker diarization and recognition

Physical inference can identify speaker clusters/embeddings, but assigning semantic identity such as "user" or a named colleague belongs to the Module.

**Owner:** `Capability adapter` for physical inference; `Module` for meaning.

### Keyword/wake-word mechanisms

PocketSphinx-style grammars/keyphrases and sherpa keyword spotting may be attractive for always-on constrained interactions where full ASR residency is wasteful.

**Owner:** `Capability adapter`.

### Neural local TTS

Piper and sherpa-onnx are plausible mechanisms, but OS-native synthesis or eSpeak-class engines can provide cheaper initial coverage.

**Owner:** `Capability adapter`.

## 12. Smallest useful voice-enablement direction

The smallest evidence-supported direction is:

```text
Module interaction
        |
        | requests microphone-derived text
        v
provider-neutral SDK computation
    audio capture
    audio -> text
        |
        v
Kernel
    selects installed mechanisms
        |
        +-- OS-native recognizer
        +-- whisper.cpp
        +-- sherpa-onnx
        +-- Vosk / PocketSphinx
        +-- remote ASR
```

A first useful slice should be limited to:

1. ordinary microphone capture;
2. a typed audio representation/stream;
3. provider-independent `audio -> text` computation;
4. runtime Capability discovery/probing;
5. cancellation and sufficiently explicit final-result semantics;
6. at least two deliberately different adapters so provider neutrality is demonstrated rather than merely documented.

A particularly useful proof pair would be an OS-native recognizer plus a portable local runtime such as whisper.cpp. This tests the abstraction more strongly than choosing two similar neural runtimes.

Do **not** infer from this audit that an initial voice slice should also include global hotkeys, automatic paste, meetings, system audio, diarization, speaker identity, AEC, notes, semantic search, agent mode or cloud sync.

## 13. Representative Capability mechanisms

A single machine could legitimately expose all of these:

```text
Capability A
Windows installed grammar recognizer
- local for applicable grammar mode
- tiny MADRE startup/distribution cost
- restricted vocabulary
- very low resource demand

Capability B
Vosk small model
- local
- small model footprint
- streaming
- broader vocabulary

Capability C
whisper.cpp + selected model
- local
- model-dependent memory/residency
- broad general dictation

Capability D
sherpa-onnx + selected streaming model
- local
- model-dependent hundreds-of-MB scale
- true streaming/partial output depending on model

Capability E
remote streaming ASR
- no significant local model residency
- network required
- external disclosure/cost constraints

Capability F
PocketSphinx constrained grammar
- local
- compact
- strong fit for small deterministic command vocabularies
```

Kernel may make different selections for a six-command control vocabulary, a long multilingual recording, low-latency partial dictation, a strict no-network privacy requirement or a machine with little RAM and no accelerator. There is no universal winner.

## 14. Concrete OpenWhispr source surfaces worth studying

- `CLAUDE.md` — architectural map, native resources and runtime boundaries.
- `package.json` — build/download split across platform helpers and runtimes.
- `electron-builder.json` — packaged binary/native dependency closure.
- `resources/windows-system-audio-helper.c` — WASAPI process-loopback technique.
- `resources/windows-mic-listener.c` — event-driven Windows microphone activity.
- `resources/macos-mic-listener.swift` — CoreAudio activity monitoring.
- macOS audio-tap implementation — process/system-audio capture.
- `src/helpers/audioActivityDetector.js` — multi-platform event-first/fallback behavior.
- Whisper/sherpa helper and server code — runtime/model lifecycle.
- streaming result handling/tests — partial/final/fallback semantics.
- `src/helpers/onnxWorkerClient.js` and `src/workers/onnxWorker.js` — native-runtime isolation.
- model/download utilities — asset/platform relationships.
- `examples/custom-asr-shim/` — provider-wire-format translation example.
- `SECURITY.md` — native/plugin/supply-chain threat surface.

Prefer studying upstream projects directly for reusable physical computation: whisper.cpp, sherpa-onnx, Vosk, PocketSphinx, ONNX Runtime, miniaudio, CPAL, Silero VAD, WebRTC VAD, eSpeak NG and native Apple/Microsoft speech/audio APIs.

## 15. MADRE assumptions challenged by ecosystem evidence

The audit found **no evidence contradicting the core Module/SDK/Kernel/Capability split**. It instead challenges four possible implicit assumptions:

1. **OS-native does not necessarily mean offline/local.** Locality is a property of the concrete API/mode and current platform support.
2. **Capability properties are not purely static installation metadata.** Assets, permissions, architecture, process health, devices and fallback state alter what can actually execute.
3. **`audio -> text` can remain a useful conceptual operation, but a synchronous final string is insufficient for all workloads.** Live speech needs partial/final/cancelled/truncated failure semantics.
4. **Speech recognition should not own the audio device.** Capture, preprocessing, ASR, diarization and output can be composed from independent mechanisms.

These are evidence for refining existing concepts only when current contracts prove insufficient; they are not permission to manufacture new abstractions in advance.

## 16. Opportunities ranked by expected value versus cost

| Rank | Opportunity | Expected value | Implementation/dependency cost | Assessment |
| ---: | --- | --- | --- | --- |
| 1 | runtime-truthful Capability availability/probing and visible fallback | very high | low-medium | benefits voice and every future native/hardware Capability |
| 2 | OS-native STT/TTS adapters | high | low-medium per OS | maximum benefit on constrained machines with little distribution cost |
| 3 | whisper.cpp ASR adapter | very high | medium | strong portable local mechanism |
| 4 | minimal microphone capture/playback helper using an established library | very high | medium | foundational and provider-independent |
| 5 | streaming/partial/final/cancellation contract validation | high | medium | needed before serious live voice and reusable elsewhere |
| 6 | sherpa-onnx Capability adapter | high | medium | unlocks streaming ASR and several later audio mechanisms |
| 7 | lightweight VAD adapter(s) | medium-high | low-medium | avoids tying endpointing/silence handling to one ASR engine |
| 8 | Capability asset provenance/cache/checksum lifecycle | high long-term | medium | valuable once downloadable runtimes/models proliferate |
| 9 | Vosk/PocketSphinx low-resource adapters | medium | low-medium | demonstrates that selection is not "latest ML wins" |
| 10 | OS-native TTS plus compact fallback | medium-high | low | cheap speech output |
| 11 | system/process audio capture | medium | high | difficult cross-platform problem; defer until needed |
| 12 | diarization/speaker embeddings | medium | high | useful for meeting Modules, unnecessary for basic interaction |
| 13 | AEC/duplex meeting processing | medium | high | only valuable with simultaneous mic/system audio |
| 14 | neural TTS such as Piper | medium | medium-high | quality improvement after cheaper mechanisms prove insufficient |
| 15 | OpenWhispr whole-application integration | low | very high | avoid |

## Final advisory classification

**Adopt:** proven upstream physical runtimes and lightweight audio libraries where exact licensing/resource properties fit; especially OS-native services, whisper.cpp, sherpa-onnx and possibly miniaudio/CPAL.

**Adapt:** OpenWhispr's platform-helper techniques, process isolation, event-driven device monitoring, streaming finalization, asset lifecycle and visible fallback handling — as small MADRE-owned helpers/adapters rather than as an OpenWhispr dependency.

**Generalize:** runtime-observed Capability properties, modality-specific audio facts, device/resource requirements, and explicit streaming/cancellation/finalization semantics, but only where current generic MADRE contracts are shown to be insufficient.

**Avoid:** OpenWhispr's whole Electron/product stack, OpenAI wire formats as SDK architecture, hotkey/auto-paste behavior in voice APIs, silent mechanism fallback, bundled unrelated inference/search/agent infrastructure, and a generic `VoiceProvider` framework.

**Investigate later:** system audio, AEC, diarization, speaker identification, wake words, richer neural TTS and continuous VAD when concrete Module/product pressure exists.

The durable conclusion is conservative:

> MADRE does not need OpenWhispr to obtain voice. OpenWhispr is valuable because it demonstrates how voice decomposes into independently useful computations, where cross-platform implementations fail, and why those computations should remain independently selectable physical Capabilities.

## Source inventory

### OpenWhispr primary sources

- Website: https://openwhispr.com/
- Main repository: https://github.com/OpenWhispr/openwhispr
- Snapshot reviewed: https://github.com/OpenWhispr/openwhispr/tree/a2c76ef9ce2f121f5e1bd32af8a7530c8f86aff7
- Technical architecture reference: https://github.com/OpenWhispr/openwhispr/blob/a2c76ef9ce2f121f5e1bd32af8a7530c8f86aff7/CLAUDE.md
- Package/build manifest: https://github.com/OpenWhispr/openwhispr/blob/a2c76ef9ce2f121f5e1bd32af8a7530c8f86aff7/package.json
- Packaging manifest: https://github.com/OpenWhispr/openwhispr/blob/a2c76ef9ce2f121f5e1bd32af8a7530c8f86aff7/electron-builder.json
- Native resources tree: https://github.com/OpenWhispr/openwhispr/tree/a2c76ef9ce2f121f5e1bd32af8a7530c8f86aff7/resources
- Helper implementation tree: https://github.com/OpenWhispr/openwhispr/tree/a2c76ef9ce2f121f5e1bd32af8a7530c8f86aff7/src/helpers
- Worker implementation tree: https://github.com/OpenWhispr/openwhispr/tree/a2c76ef9ce2f121f5e1bd32af8a7530c8f86aff7/src/workers
- Custom ASR shim example: https://github.com/OpenWhispr/openwhispr/tree/a2c76ef9ce2f121f5e1bd32af8a7530c8f86aff7/examples/custom-asr-shim
- Security notes: https://github.com/OpenWhispr/openwhispr/blob/a2c76ef9ce2f121f5e1bd32af8a7530c8f86aff7/SECURITY.md
- Issue tracker/failure corpus: https://github.com/OpenWhispr/openwhispr/issues
- MCP companion repository: https://github.com/OpenWhispr/openwhispr-mcp
- CLI companion repository: https://github.com/OpenWhispr/openwhispr-cli

### Speech/audio mechanisms and runtimes

- whisper.cpp: https://github.com/ggml-org/whisper.cpp
- sherpa-onnx: https://github.com/k2-fsa/sherpa-onnx
- Vosk: https://github.com/alphacep/vosk-api
- PocketSphinx: https://github.com/cmusphinx/pocketsphinx
- ONNX Runtime: https://github.com/microsoft/onnxruntime
- miniaudio: https://github.com/mackron/miniaudio
- CPAL: https://github.com/RustAudio/cpal
- Silero VAD: https://github.com/snakers4/silero-vad
- WebRTC VAD reference wrapper/source pointer: https://github.com/wiseman/py-webrtcvad
- eSpeak NG: https://github.com/espeak-ng/espeak-ng
- Piper current project: https://github.com/OHF-Voice/piper1-gpl
- archived original Piper: https://github.com/rhasspy/piper

### OS-native platform documentation

- Windows speech recognition: https://learn.microsoft.com/windows/apps/design/input/speech-recognition
- Windows `SpeechRecognizer`: https://learn.microsoft.com/uwp/api/windows.media.speechrecognition.speechrecognizer
- Windows speech synthesis: https://learn.microsoft.com/uwp/api/windows.media.speechsynthesis.speechsynthesizer
- Windows application-loopback audio sample: https://learn.microsoft.com/samples/microsoft/windows-classic-samples/applicationloopbackaudio-sample/
- Apple Speech framework: https://developer.apple.com/documentation/speech
- Apple on-device recognition support: https://developer.apple.com/documentation/speech/sfspeechrecognizer/supportsondevicerecognition
- Apple speech synthesis: https://developer.apple.com/documentation/avfaudio/avspeechsynthesizer
- Apple Core Audio: https://developer.apple.com/documentation/coreaudio

## Reading rule

This audit records opportunity evidence, not requirements. Before adopting any dependency or changing MADRE contracts, verify the current license, release status, model-license provenance, platform support and resource characteristics of the exact version under consideration.
