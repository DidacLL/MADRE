using System.Net.Sockets;
using Madre.Kernel.Host;

internal static partial class Program
{
    private static async Task SingleInstanceSafetyAsync()
    {
        using var temp = new TempDir("madre-owner");
        string database = Path.Combine(temp.Path, "owner.db");
        string firstSocket = NewSocketPath();
        string secondSocket = NewSocketPath();
        await using RawHost first = await RawHost.StartHealthyAsync(null, database, firstSocket);

        HostExit sameSocket = await RunHostToExitAsync(["--db", database, "--ipc-path", firstSocket]);
        Check(sameSocket.ExitCode != 0 && sameSocket.Log.Contains("active process owner", StringComparison.OrdinalIgnoreCase),
            "second Kernel acquired same database/same socket");
        Check((await new IpcClient(firstSocket).CallAsync<Health>("Health", null)).Status == "ok",
            "same-socket contender disturbed live Kernel");

        HostExit differentSocket = await RunHostToExitAsync(["--db", database, "--ipc-path", secondSocket]);
        Check(differentSocket.ExitCode != 0 && differentSocket.Log.Contains("active process owner", StringComparison.OrdinalIgnoreCase),
            "second Kernel acquired same database through a different socket");
        Check(!File.Exists(secondSocket), "database contender created an alternate IPC endpoint");
        Check((await new IpcClient(firstSocket).CallAsync<Health>("Health", null)).Status == "ok",
            "different-socket contender disturbed live Kernel");

        HostExit liveEndpoint = await RunHostToExitAsync([
            "--db", Path.Combine(temp.Path, "other.db"),
            "--ipc-path", firstSocket]);
        Check(liveEndpoint.ExitCode != 0, "second Kernel acquired a live IPC endpoint owned by another database");
        Check((await new IpcClient(firstSocket).CallAsync<Health>("Health", null)).Status == "ok",
            "live endpoint contender disturbed its owner");

        string staleSocket = NewSocketPath();
        using (var stale = new Socket(AddressFamily.Unix, SocketType.Stream, ProtocolType.Unspecified))
        {
            stale.Bind(new UnixDomainSocketEndPoint(staleSocket));
            stale.Listen(1);
        }
        if (File.Exists(staleSocket))
        {
            await using RawHost recovered = await RawHost.StartHealthyAsync(
                null,
                Path.Combine(temp.Path, "stale.db"),
                staleSocket);
            Check((await new IpcClient(staleSocket).CallAsync<Health>("Health", null)).Status == "ok",
                "stale socket path was not recovered safely");
        }

        Console.WriteLine("PASS one process owner per database and safe live/stale endpoint handling");
    }
}
