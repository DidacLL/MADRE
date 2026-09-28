from pathlib import Path


def replace_once(path: str, old: str, new: str, label: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    p.write_text(text.replace(old, new))


replace_once(
    "tests/Madre.Kernel.Verification/IntegrationVerification.cs",
    '''        var contenders = Enumerable.Range(0, 8).Select(_ => RawHost.StartConfigured(null, ownerDb, NewSocketPath())).ToArray();
        await Task.Delay(500); int alive = 0; foreach (RawHost h in contenders) { try { _ = await h.WaitForExitAsync(100); } catch (TimeoutException) { alive++; } } Check(alive == 0, "losing owners remained alive while established owner held database"); foreach (RawHost h in contenders) await h.DisposeAsync();
''',
    '''        var contenders = Enumerable.Range(0, 8).Select(_ => RawHost.StartConfigured(null, ownerDb, NewSocketPath())).ToArray();
        HostExit[] contenderExits = await Task.WhenAll(contenders.Select(host => host.WaitForExitAsync(5_000)));
        Check(contenderExits.All(exit => exit.ExitCode != 0), "established database owner admitted a losing contender");
        foreach (RawHost h in contenders) await h.DisposeAsync();
''',
    "ownership contender watchdog",
)

replace_once(
    "tests/Madre.Kernel.Acceptance/AuditSupport.cs",
    '''            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true
        };
        psi.ArgumentList.Add("-cp");
''',
    '''            RedirectStandardOutput = true,
            RedirectStandardError = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardErrorEncoding = Encoding.UTF8,
            UseShellExecute = false,
            CreateNoWindow = true
        };
        psi.ArgumentList.Add("-Dfile.encoding=UTF-8");
        psi.ArgumentList.Add("-Dstdout.encoding=UTF-8");
        psi.ArgumentList.Add("-Dstderr.encoding=UTF-8");
        psi.ArgumentList.Add("-cp");
''',
    "Java helper UTF-8 process boundary",
)

replace_once(
    "tests/Madre.Kernel.Verification/StressVerification.cs",
    '''            await using var engine = new KernelEngine(new WorkStore(Path.Combine(temp.Path, "load.db")), [cap], [binding], slots); await engine.InitializeAsync(); await engine.RefreshCapabilityStatesAsync(); engine.Start(); int count = Math.Max(slots * 10, requested); var ids = new List<string>(count); for (int i = 0; i < count; i++) ids.Add(await engine.SubmitAsync(Req($"w:{i}", InferenceEffort.Low, (WorkUrgency)(i % 3), ExecutionBoundary.LocalOnly))); await WaitAllTerminalAsync(engine, ids, Math.Max(30000, count * 20)); IReadOnlyList<WorkInspection> works = await InspectAllAsync(engine, ids); AssertGlobalPhysicalInvariants(works, binding.MaxActive, slots); Check(works.All(w => w.Attempts.Count == 1) && binding.Active == 0, $"attempt/slot leak at maxConcurrent={slots}");
''',
    '''            await using var engine = new KernelEngine(new WorkStore(Path.Combine(temp.Path, "load.db")), [cap], [binding], slots); await engine.InitializeAsync(); await engine.RefreshCapabilityStatesAsync(); engine.Start(); int count = Math.Max(slots * 10, requested); var ids = new List<string>(count); for (int i = 0; i < count; i++) ids.Add(await engine.SubmitAsync(Req($"w:{i}", InferenceEffort.Low, (WorkUrgency)(i % 3), ExecutionBoundary.LocalOnly))); await WaitUntilAsync(() => binding.ExecutionCount == count && binding.Active == 0, Math.Max(120_000, count * 100), $"execution load did not drain at maxConcurrent={slots}"); await WaitAllTerminalAsync(engine, ids, 30_000); IReadOnlyList<WorkInspection> works = await InspectAllAsync(engine, ids); AssertGlobalPhysicalInvariants(works, binding.MaxActive, slots); Check(works.All(w => w.Attempts.Count == 1) && binding.Active == 0, $"attempt/slot leak at maxConcurrent={slots}");
''',
    "stress completion watchdog",
)

replace_once(
    "tests/Madre.Kernel.Verification/StressVerification.cs",
    '''        for (int i = 0; i < iterations; i++) { int kind = random.Next(6); if (kind == 0) _ = await SendRawFrameAsync(env.Socket, random.Next(2)==0?0:-1, ReadOnlyMemory<byte>.Empty); else if (kind == 1) { byte[] b = new byte[random.Next(1,128)]; random.NextBytes(b); _ = await SendRawFrameAsync(env.Socket, b.Length, b); } else if (kind == 2) _ = await RawIpcAsync(env.Socket, $"{{\\"version\\":{KernelProtocol.Version},\\"requestId\\":\\"{i}😀\\",\\"operation\\":\\"NoSuch\\",\\"payload\\":null}}"); else if (kind == 3) _ = await RawIpcAsync(env.Socket, "{"); else { int n = kind == 4 ? KernelProtocol.MaxPayloadBytes : KernelProtocol.MaxPayloadBytes + 1; _ = await RawIpcAsync(env.Socket, JsonSerializer.Serialize(new { version=KernelProtocol.Version, requestId=$"p{i}", operation="Submit", payload=new { preparedInput=new string('x',n), requestedEffort="Low", urgency="Normal", executionBoundary="LocalOnly" } })); } if (i % 50 == 0) Check((await client.CallAsync<Health>("Health", null)).Status == "ok", $"fuzz damaged host seed={seed} i={i}"); }
''',
    '''        for (int i = 0; i < iterations; i++) { int kind = random.Next(6); if (kind == 0) _ = await SendRawFrameAsync(env.Socket, random.Next(2)==0?0:-1, ReadOnlyMemory<byte>.Empty); else if (kind == 1) { byte[] b = new byte[random.Next(1,128)]; random.NextBytes(b); _ = await SendRawFrameAsync(env.Socket, b.Length, b); } else if (kind == 2) _ = await RawIpcAsync(env.Socket, $"{{\\"version\\":{KernelProtocol.Version},\\"requestId\\":\\"{i}😀\\",\\"operation\\":\\"NoSuch\\",\\"payload\\":null}}"); else if (kind == 3) _ = await RawIpcAsync(env.Socket, "{"); else { int n = kind == 4 ? KernelProtocol.MaxPayloadBytes : KernelProtocol.MaxPayloadBytes + 1; object payload = kind == 4 ? new { preparedInput=new string('x',n), requestedEffort=0, urgency="Normal", executionBoundary="LocalOnly" } : new { preparedInput=new string('x',n), requestedEffort="Low", urgency="Normal", executionBoundary="LocalOnly" }; _ = await RawIpcAsync(env.Socket, JsonSerializer.Serialize(new { version=KernelProtocol.Version, requestId=$"p{i}", operation="Submit", payload })); } if (i % 50 == 0) Check((await client.CallAsync<Health>("Health", null)).Status == "ok", $"fuzz damaged host seed={seed} i={i}"); }
''',
    "fuzz must not create durable valid Work backlog",
)

replace_once(
    "tests/Madre.Kernel.Verification/AdditionalVerification.cs",
    '''                File.WriteAllText(env.SlowState, "unavailable");
                _ = await client.CallAsync<List<CapabilitySnapshot>>("RefreshCapabilities", null);
                File.WriteAllText(env.SlowState, "available");
                _ = await client.CallAsync<List<CapabilitySnapshot>>("RefreshCapabilities", null);
''',
    '''                await WriteProbeStateAsync(env.SlowState, "unavailable");
                _ = await client.CallAsync<List<CapabilitySnapshot>>("RefreshCapabilities", null);
                await WriteProbeStateAsync(env.SlowState, "available");
                _ = await client.CallAsync<List<CapabilitySnapshot>>("RefreshCapabilities", null);
''',
    "soak probe-state writer",
)

additional = Path("tests/Madre.Kernel.Verification/AdditionalVerification.cs")
text = additional.read_text()
marker = '''    private sealed record SoakAttemptDiagnostics(int MaxConcurrency, IReadOnlyList<long> Latencies);
'''
helper = '''    private static async Task WriteProbeStateAsync(string path, string value)
    {
        for (int attempt = 0; ; attempt++)
        {
            try
            {
                await File.WriteAllTextAsync(path, value).ConfigureAwait(false);
                return;
            }
            catch (IOException) when (OperatingSystem.IsWindows() && attempt < 100)
            {
                await Task.Delay(10).ConfigureAwait(false);
            }
        }
    }

'''
if text.count(marker) != 1 or "WriteProbeStateAsync(string path" in text:
    raise SystemExit("soak writer helper insertion point invalid")
additional.write_text(text.replace(marker, helper + marker))
