using System.Buffers.Binary;
using System.Diagnostics;
using System.Net.Sockets;
using Madre.Kernel;
using Madre.Kernel.Host;

internal static partial class Program
{
    private static async Task IpcBoundednessAndUtf8Async()
    {
        using var temp = new TempDir("madre-bounds-utf8");
        TestEnv env = MakeEnv(temp.Path, true, true);
        File.WriteAllText(env.SlowState, "available");
        File.WriteAllText(env.FastState, "unavailable");
        await using RawHost host = await RawHost.StartHealthyAsync(env.Config, env.Database, env.Socket, 1);
        var client = new IpcClient(env.Socket);
        await WaitCapabilityAsync(client, Slow, CapabilityAvailability.Available);

        var stalled = new List<Socket>();
        try
        {
            byte[] header = new byte[4];
            BinaryPrimitives.WriteInt32BigEndian(header, 100);
            for (int i = 0; i < KernelIpcDefaults.MaxActiveClients; i++)
            {
                var socket = new Socket(AddressFamily.Unix, SocketType.Stream, ProtocolType.Unspecified);
                await socket.ConnectAsync(new UnixDomainSocketEndPoint(env.Socket));
                await socket.SendAsync(header, SocketFlags.None);
                await socket.SendAsync(new byte[] { (byte)'{' }, SocketFlags.None);
                stalled.Add(socket);
            }
            await Task.Delay(150);
            bool bounded = false;
            try
            {
                _ = await client.CallAsync<Health>("Health", null).WaitAsync(TimeSpan.FromSeconds(1));
            }
            catch (Exception)
            {
                bounded = true;
            }
            Check(bounded, "server admitted unbounded active stalled client handlers");
        }
        finally
        {
            foreach (Socket socket in stalled)
            {
                socket.Dispose();
            }
        }

        await Task.Delay(150);
        Check((await client.CallAsync<Health>("Health", null)).Status == "ok",
            "bounded client saturation did not recover after callers disappeared");

        string unicode = "Zażółć gęślą jaźń — català 中文 🚀";
        string utf8Work = await SubmitAsync(client, Req(
            unicode,
            InferenceEffort.Standard,
            WorkUrgency.Normal,
            ExecutionBoundary.LocalOnly));
        await WaitStateAsync(client, utf8Work, WorkState.Succeeded);
        Check((await client.CallAsync<WorkResultSnapshot>("Result", new WorkIdArg(utf8Work))).Result == "slow:" + unicode,
            "process binding did not round-trip UTF-8 exactly");

        string bogusSocket = NewSocketPath();
        using var bogus = new Socket(AddressFamily.Unix, SocketType.Stream, ProtocolType.Unspecified);
        bogus.Bind(new UnixDomainSocketEndPoint(bogusSocket));
        bogus.Listen(1);
        Task<Socket> accepted = bogus.AcceptAsync();
        Stopwatch timeout = Stopwatch.StartNew();
        Task<ProcessExit> javaCall = RunJavaToExitAsync(bogusSocket, "protocol");
        using Socket peer = await accepted.WaitAsync(TimeSpan.FromSeconds(3));
        ProcessExit javaExit = await javaCall.WaitAsync(TimeSpan.FromSeconds(8));
        timeout.Stop();
        Check(javaExit.ExitCode != 0
            && timeout.Elapsed < TimeSpan.FromSeconds(8)
            && javaExit.Stderr.Contains("timeout", StringComparison.OrdinalIgnoreCase),
            "Java client blocked indefinitely against a stalled local peer");

        Console.WriteLine("PASS bounded IPC handlers/Java timeout and explicit UTF-8 process binding");
    }
}
