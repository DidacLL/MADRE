from pathlib import Path


def replace_once(path: str, old: str, new: str, label: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    p.write_text(text.replace(old, new))


replace_once(
    "tests/Madre.Kernel.Verification/ExpandedIntegrationVerification.cs",
    '''        await Task.Delay(750);
        var survivors = new List<(RawHost Host, string Socket)>();
        foreach ((RawHost host, string socket) in contenders)
        {
            try
            {
                _ = await host.WaitForExitAsync(150);
            }
            catch (TimeoutException)
            {
                survivors.Add((host, socket));
            }
        }
        Check(survivors.Count == 1, $"simultaneous Kernel startups produced {survivors.Count} owners instead of one");
        Check((await new IpcClient(survivors[0].Socket).CallAsync<Health>("Health", null)).Status == "ok",
            "losing ownership contenders damaged the winning Kernel");
''',
    '''        int winnerIndex = -1;
        Stopwatch ownershipWait = Stopwatch.StartNew();
        while (ownershipWait.Elapsed < TimeSpan.FromSeconds(10))
        {
            bool[] healthy = await Task.WhenAll(contenders.Select(async contender =>
            {
                try
                {
                    return (await new IpcClient(contender.Socket).CallAsync<Health>("Health", null)).Status == "ok";
                }
                catch (Exception ex) when (ex is SocketException or IOException or InvalidOperationException)
                {
                    return false;
                }
            }));
            int[] healthyIndexes = healthy
                .Select((value, index) => (value, index))
                .Where(item => item.value)
                .Select(item => item.index)
                .ToArray();
            Check(healthyIndexes.Length <= 1,
                $"simultaneous Kernel startups produced {healthyIndexes.Length} healthy owners");
            if (healthyIndexes.Length == 1)
            {
                winnerIndex = healthyIndexes[0];
                break;
            }
            await Task.Delay(50);
        }
        Check(winnerIndex >= 0, "simultaneous Kernel startups produced no healthy owner");

        for (int i = 0; i < contenders.Count; i++)
        {
            if (i == winnerIndex)
            {
                continue;
            }
            HostExit loserExit;
            try
            {
                loserExit = await contenders[i].Host.WaitForExitAsync(10_000);
            }
            catch (TimeoutException)
            {
                throw new InvalidOperationException($"losing ownership contender {i} did not terminate");
            }
            Check(loserExit.ExitCode != 0, $"losing ownership contender {i} exited successfully");
        }
        Check((await new IpcClient(contenders[winnerIndex].Socket).CallAsync<Health>("Health", null)).Status == "ok",
            "losing ownership contenders damaged the winning Kernel");
''',
    "simultaneous ownership eventual-state verification",
)

replace_once(
    "tests/Madre.Kernel.Acceptance/Support.cs",
    '''    private static async Task<string> JavaAsync(string socket, params string[] args)
    {
        var psi = new ProcessStartInfo { FileName = "java", RedirectStandardOutput = true, RedirectStandardError = true, UseShellExecute = false, CreateNoWindow = true };
        psi.ArgumentList.Add("-cp"); psi.ArgumentList.Add(JavaClasspath); psi.ArgumentList.Add("io.github.didacll.madre.kernel.client.KernelClientProcess"); psi.ArgumentList.Add(socket); foreach (string a in args) psi.ArgumentList.Add(a);
''',
    '''    private static async Task<string> JavaAsync(string socket, params string[] args)
    {
        var psi = new ProcessStartInfo
        {
            FileName = "java",
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardErrorEncoding = Encoding.UTF8,
            UseShellExecute = false,
            CreateNoWindow = true
        };
        psi.ArgumentList.Add("-Dfile.encoding=UTF-8");
        psi.ArgumentList.Add("-Dstdout.encoding=UTF-8");
        psi.ArgumentList.Add("-Dstderr.encoding=UTF-8");
        psi.ArgumentList.Add("-cp"); psi.ArgumentList.Add(JavaClasspath); psi.ArgumentList.Add("io.github.didacll.madre.kernel.client.KernelClientProcess"); psi.ArgumentList.Add(socket); foreach (string a in args) psi.ArgumentList.Add(a);
''',
    "JavaAsync explicit UTF-8 boundary",
)

replace_once(
    "tests/Madre.Kernel.Verification/AdditionalVerification.cs",
    '''        File.WriteAllText(env.SlowState, "available");
        _ = await client.CallAsync<List<CapabilitySnapshot>>("RefreshCapabilities", null);

        int completed = 0;
''',
    '''        await WriteProbeStateAsync(env.SlowState, "available");
        _ = await client.CallAsync<List<CapabilitySnapshot>>("RefreshCapabilities", null);

        int completed = 0;
''',
    "post-restart soak probe-state writer",
)
