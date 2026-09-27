using System.Buffers.Binary;
using System.Diagnostics;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using Madre.Kernel;
using Madre.Kernel.Host;
using Microsoft.Data.Sqlite;

internal static partial class Program
{
    private static bool IsInvalidRequest(JsonDocument? document) =>
        document is not null
        && document.RootElement.TryGetProperty("ok", out JsonElement ok)
        && !ok.GetBoolean()
        && document.RootElement.GetProperty("error").GetProperty("code").GetString() == "InvalidRequest";

    private static void AssertInvalidConfig(string path, string json, string message)
    {
        File.WriteAllText(path, json);
        bool rejected = false;
        try
        {
            _ = KernelConfigurationLoader.Load(path);
        }
        catch (InvalidDataException)
        {
            rejected = true;
        }
        Check(rejected, message);
    }

    private static async Task<JsonDocument?> RawIpcAsync(string socketPath, string json)
    {
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(2));
        try
        {
            byte[] body = Encoding.UTF8.GetBytes(json);
            using var socket = new Socket(AddressFamily.Unix, SocketType.Stream, ProtocolType.Unspecified);
            await socket.ConnectAsync(new UnixDomainSocketEndPoint(socketPath), timeout.Token);
            using var stream = new NetworkStream(socket, ownsSocket: false);
            byte[] header = new byte[4];
            BinaryPrimitives.WriteInt32BigEndian(header, body.Length);
            await stream.WriteAsync(header, timeout.Token);
            await stream.WriteAsync(body, timeout.Token);
            await stream.FlushAsync(timeout.Token);
            await stream.ReadExactlyAsync(header, timeout.Token);
            int length = BinaryPrimitives.ReadInt32BigEndian(header);
            Check(length > 0 && length <= KernelProtocol.MaxFrameBytes, "raw IPC response outside frame bound");
            byte[] response = new byte[length];
            await stream.ReadExactlyAsync(response, timeout.Token);
            return JsonDocument.Parse(response);
        }
        catch (Exception ex) when (ex is IOException or SocketException or OperationCanceledException)
        {
            return null;
        }
    }

    private static async Task ExecuteSqlAsync(string database, string sql)
    {
        SqliteConnection.ClearAllPools();
        await using var connection = new SqliteConnection($"Data Source={database}");
        await connection.OpenAsync();
        await using SqliteCommand command = connection.CreateCommand();
        command.CommandText = sql;
        await command.ExecuteNonQueryAsync();
    }

    private static async Task WaitUntilAsync(Func<bool> predicate, int timeoutMs, string message)
    {
        Stopwatch sw = Stopwatch.StartNew();
        while (sw.ElapsedMilliseconds < timeoutMs)
        {
            if (predicate())
            {
                return;
            }
            await Task.Delay(20);
        }
        throw new InvalidOperationException(message);
    }

    private static async Task<CapabilitySnapshot> WaitSnapshotAsync(
        KernelEngine engine,
        string capabilityId,
        Func<CapabilitySnapshot, bool> predicate,
        int timeoutMs)
    {
        Stopwatch sw = Stopwatch.StartNew();
        while (sw.ElapsedMilliseconds < timeoutMs)
        {
            CapabilitySnapshot snapshot = (await engine.CapabilitiesAsync()).Single(x => x.Capability.CapabilityId == capabilityId);
            if (predicate(snapshot))
            {
                return snapshot;
            }
            await Task.Delay(20);
        }
        throw new InvalidOperationException($"capability {capabilityId} did not reach expected observation state");
    }

    private static async Task<HostExit> RunHostToExitAsync(string[] arguments)
    {
        await using RawHost host = RawHost.Start(arguments);
        return await host.WaitForExitAsync(5000);
    }

    private static async Task<ProcessExit> RunJavaToExitAsync(string socket, params string[] args)
    {
        var psi = new ProcessStartInfo
        {
            FileName = "java",
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true
        };
        psi.ArgumentList.Add("-cp");
        psi.ArgumentList.Add(JavaClasspath);
        psi.ArgumentList.Add("io.github.didacll.madre.kernel.client.KernelClientProcess");
        psi.ArgumentList.Add(socket);
        foreach (string arg in args)
        {
            psi.ArgumentList.Add(arg);
        }

        using Process process = Process.Start(psi) ?? throw new InvalidOperationException("failed to launch Java helper");
        Task<string> stdout = process.StandardOutput.ReadToEndAsync();
        Task<string> stderr = process.StandardError.ReadToEndAsync();
        await process.WaitForExitAsync();
        return new ProcessExit(process.ExitCode, await stdout, await stderr);
    }

    private sealed class CountingBinding(
        string bindingId,
        string bindingVersion,
        CapabilityAvailability initialAvailability) : IInferenceBinding
    {
        private int _probeCount;
        private volatile CapabilityAvailability _availability = initialAvailability;

        public string BindingId => bindingId;
        public string BindingVersion => bindingVersion;
        public CapabilityAvailability Availability
        {
            get => _availability;
            set => _availability = value;
        }
        public int ProbeCount => Volatile.Read(ref _probeCount);

        public Task<CapabilityAvailability> ProbeAsync(CancellationToken cancellationToken)
        {
            Interlocked.Increment(ref _probeCount);
            return Task.FromResult(_availability);
        }

        public Task<BindingExecutionResult> ExecuteAsync(InferenceExecutionRequest request, CancellationToken cancellationToken) =>
            Task.FromResult(BindingExecutionResult.Success("ok:" + request.PreparedInput));
    }

    private sealed class VersionedBinding(string bindingId, string bindingVersion, int delayMs) : IInferenceBinding
    {
        public string BindingId => bindingId;
        public string BindingVersion => bindingVersion;

        public Task<CapabilityAvailability> ProbeAsync(CancellationToken cancellationToken) =>
            Task.FromResult(CapabilityAvailability.Available);

        public async Task<BindingExecutionResult> ExecuteAsync(InferenceExecutionRequest request, CancellationToken cancellationToken)
        {
            await Task.Delay(delayMs, cancellationToken);
            return BindingExecutionResult.Success(request.PreparedInput);
        }
    }

    private sealed record HostExit(int ExitCode, string Log);
    private sealed record ProcessExit(int ExitCode, string Stdout, string Stderr);

    private sealed class RawHost : IAsyncDisposable
    {
        private readonly Process _process;
        private readonly Task<string> _stdout;
        private readonly Task<string> _stderr;
        private bool _disposed;

        private RawHost(Process process)
        {
            _process = process;
            _stdout = process.StandardOutput.ReadToEndAsync();
            _stderr = process.StandardError.ReadToEndAsync();
        }

        public static RawHost Start(string[] arguments)
        {
            var psi = new ProcessStartInfo
            {
                FileName = Dotnet,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true
            };
            psi.ArgumentList.Add(HostDll);
            foreach (string argument in arguments)
            {
                psi.ArgumentList.Add(argument);
            }
            Process process = Process.Start(psi) ?? throw new InvalidOperationException("failed to launch Kernel host");
            return new RawHost(process);
        }

        public static RawHost StartConfigured(string? config, string database, string socket, int? maxConcurrent = null) =>
            Start(HostArguments(config, database, socket, maxConcurrent));

        public static async Task<RawHost> StartHealthyAsync(
            string? config,
            string database,
            string socket,
            int? maxConcurrent = null)
        {
            RawHost host = StartConfigured(config, database, socket, maxConcurrent);
            try
            {
                Stopwatch sw = Stopwatch.StartNew();
                var client = new IpcClient(socket);
                while (sw.Elapsed < TimeSpan.FromSeconds(7))
                {
                    if (host._process.HasExited)
                    {
                        HostExit exit = await host.WaitForExitAsync(1000);
                        throw new InvalidOperationException("Kernel exited before health: " + exit.Log);
                    }
                    try
                    {
                        if ((await client.CallAsync<Health>("Health", null)).Status == "ok")
                        {
                            return host;
                        }
                    }
                    catch (Exception ex) when (ex is SocketException or IOException or InvalidOperationException)
                    {
                    }
                    await Task.Delay(30);
                }
                throw new InvalidOperationException("Kernel did not become healthy");
            }
            catch
            {
                await host.DisposeAsync();
                throw;
            }
        }

        public async Task<HostExit> WaitForExitAsync(int timeoutMs)
        {
            await _process.WaitForExitAsync().WaitAsync(TimeSpan.FromMilliseconds(timeoutMs));
            return new HostExit(_process.ExitCode, (await _stdout) + "\n" + (await _stderr));
        }

        public async ValueTask DisposeAsync()
        {
            if (_disposed)
            {
                return;
            }
            _disposed = true;
            if (!_process.HasExited)
            {
                _process.Kill(entireProcessTree: true);
                await _process.WaitForExitAsync();
            }
            _process.Dispose();
        }

        private static string[] HostArguments(string? config, string database, string socket, int? maxConcurrent)
        {
            var values = new List<string>();
            if (config is not null)
            {
                values.Add("--config");
                values.Add(config);
            }
            values.Add("--db");
            values.Add(database);
            values.Add("--ipc-path");
            values.Add(socket);
            if (maxConcurrent.HasValue)
            {
                values.Add("--max-concurrent");
                values.Add(maxConcurrent.Value.ToString());
            }
            return values.ToArray();
        }
    }
}
