using System.Text.Json;
using Madre.Kernel;

internal static partial class Program
{
    private static async Task StrictBoundaryAsync()
    {
        using var temp = new TempDir("madre-strict");
        string socket = NewSocketPath();
        await using RawHost host = await RawHost.StartHealthyAsync(null, Path.Combine(temp.Path, "strict.db"), socket);

        using JsonDocument? missingFields = await RawIpcAsync(socket,
            "{\"version\":1,\"requestId\":\"missing\",\"operation\":\"Submit\",\"payload\":{\"preparedInput\":\"x\"}}");
        Check(IsInvalidRequest(missingFields), "omitted submit enums acquired default enum-zero values");

        using JsonDocument? numericEnum = await RawIpcAsync(socket,
            "{\"version\":1,\"requestId\":\"numeric\",\"operation\":\"Submit\",\"payload\":{\"preparedInput\":\"x\",\"requestedEffort\":0,\"urgency\":\"Normal\",\"executionBoundary\":\"LocalOnly\"}}");
        Check(IsInvalidRequest(numericEnum), "numeric submit enum encoding was accepted");

        using JsonDocument? unknownPayload = await RawIpcAsync(socket,
            "{\"version\":1,\"requestId\":\"unknown\",\"operation\":\"Submit\",\"payload\":{\"preparedInput\":\"x\",\"requestedEffort\":\"Low\",\"urgency\":\"Normal\",\"executionBoundary\":\"LocalOnly\",\"invented\":true}}");
        Check(IsInvalidRequest(unknownPayload), "unknown submit property was accepted");

        using JsonDocument? numericOperation = await RawIpcAsync(socket,
            "{\"version\":1,\"requestId\":\"operation\",\"operation\":2,\"payload\":null}");
        Check(numericOperation is null, "numeric operation encoding was accepted");
        Check((await new IpcClient(socket).CallAsync<Health>("Health", null)).Status == "ok",
            "malformed IPC damaged the host");

        AssertInvalidConfig(Path.Combine(temp.Path, "unknown.json"),
            "{\"invented\":true}",
            "unknown config property was accepted");
        AssertInvalidConfig(Path.Combine(temp.Path, "numeric.json"),
            "{\"capabilities\":[{\"capabilityId\":\"x\",\"bindingId\":\"b\",\"bindingVersion\":\"1\",\"executable\":\"x\",\"executionBoundary\":0,\"supportedEffort\":\"Low\",\"ownerPreference\":1}]}",
            "numeric config enum was accepted");
        AssertInvalidConfig(Path.Combine(temp.Path, "missing.json"),
            "{\"capabilities\":[{\"capabilityId\":\"x\",\"bindingId\":\"b\",\"bindingVersion\":\"1\",\"executable\":\"x\",\"ownerPreference\":1}]}",
            "missing required capability fields acquired defaults");

        HostExit unknownCli = await RunHostToExitAsync(["--bogus", "x"]);
        HostExit missingCli = await RunHostToExitAsync(["--db"]);
        HostExit malformedCli = await RunHostToExitAsync([
            "--db", Path.Combine(temp.Path, "cli.db"),
            "--ipc-path", NewSocketPath(),
            "--max-concurrent", "not-an-int"]);
        Check(unknownCli.ExitCode != 0 && missingCli.ExitCode != 0 && malformedCli.ExitCode != 0,
            "malformed or unknown CLI arguments were silently accepted");
        Console.WriteLine("PASS strict IPC/config/CLI boundaries without enum/default leakage");
    }
}
