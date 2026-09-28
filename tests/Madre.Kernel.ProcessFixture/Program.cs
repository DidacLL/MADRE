using System.Diagnostics;
using System.Reflection;
using System.Text;

Console.InputEncoding = new UTF8Encoding(encoderShouldEmitUTF8Identifier: false, throwOnInvalidBytes: true);
Console.OutputEncoding = new UTF8Encoding(encoderShouldEmitUTF8Identifier: false, throwOnInvalidBytes: true);

string? probeFile = Get(args, "--probe");
if (probeFile is not null)
{
    bool available = File.Exists(probeFile)
        && string.Equals((await File.ReadAllTextAsync(probeFile)).Trim(), "available", StringComparison.OrdinalIgnoreCase);
    return available ? 0 : 12;
}

string? mode = Get(args, "--mode");
int configuredDelay = int.Parse(Get(args, "--delay-ms") ?? "0");
string prefix = Get(args, "--prefix") ?? string.Empty;

if (string.Equals(mode, "no-read-stdin", StringComparison.Ordinal))
{
    await Task.Delay(configuredDelay == 0 ? 30_000 : configuredDelay);
    return 0;
}
if (string.Equals(mode, "close-stdin", StringComparison.Ordinal))
{
    Console.OpenStandardInput().Dispose();
    await Task.Delay(configuredDelay == 0 ? 2_000 : configuredDelay);
    return 0;
}
if (string.Equals(mode, "exit-without-reading", StringComparison.Ordinal))
{
    return int.Parse(Get(args, "--exit-code") ?? "0");
}
if (string.Equals(mode, "child-sleep", StringComparison.Ordinal))
{
    string? marker = Get(args, "--marker");
    if (marker is not null)
    {
        await File.WriteAllTextAsync(marker, Environment.ProcessId.ToString());
    }
    await Task.Delay(configuredDelay == 0 ? 60_000 : configuredDelay);
    return 0;
}
if (string.Equals(mode, "spawn-child", StringComparison.Ordinal))
{
    string marker = Get(args, "--marker") ?? throw new InvalidOperationException("spawn-child requires --marker");
    string executable = Environment.ProcessPath ?? throw new InvalidOperationException("process path unavailable");
    string assembly = Assembly.GetExecutingAssembly().Location;
    var start = new ProcessStartInfo
    {
        FileName = executable,
        UseShellExecute = false,
        CreateNoWindow = true
    };
    start.ArgumentList.Add(assembly);
    start.ArgumentList.Add("--mode");
    start.ArgumentList.Add("child-sleep");
    start.ArgumentList.Add("--marker");
    start.ArgumentList.Add(marker);
    start.ArgumentList.Add("--delay-ms");
    start.ArgumentList.Add((configuredDelay == 0 ? 60_000 : configuredDelay).ToString());
    using Process child = Process.Start(start) ?? throw new InvalidOperationException("failed to start child fixture");
    await WaitForFileAsync(marker, TimeSpan.FromSeconds(5));
    await Task.Delay(configuredDelay == 0 ? 60_000 : configuredDelay);
    return 0;
}

string input = await Console.In.ReadToEndAsync();

if (string.Equals(input, "FAIL", StringComparison.Ordinal))
{
    return 17;
}
if (input.StartsWith("EXIT:", StringComparison.Ordinal))
{
    string rest = input[5..];
    int separator = rest.IndexOf('|');
    string codeText = separator < 0 ? rest : rest[..separator];
    if (separator >= 0)
    {
        await Console.Error.WriteAsync(rest[(separator + 1)..]);
    }
    return int.Parse(codeText);
}

if (TryGetByteCount(input, "FLOOD_STDOUT:", out int stdoutBytes))
{
    await FloodAsync(Console.OpenStandardOutput(), stdoutBytes);
    return 0;
}
if (TryGetByteCount(input, "FLOOD_STDERR:", out int stderrFloodBytes))
{
    await FloodAsync(Console.OpenStandardError(), stderrFloodBytes);
    return 0;
}
if (TryGetByteCount(input, "FLOOD_BOTH:", out int bothBytes))
{
    await Task.WhenAll(
        FloodAsync(Console.OpenStandardOutput(), bothBytes),
        FloodAsync(Console.OpenStandardError(), bothBytes));
    return 0;
}
if (TryGetByteCount(input, "INVALID_UTF8_STDOUT:", out int invalidStdoutBytes))
{
    Stream stdout = Console.OpenStandardOutput();
    await stdout.WriteAsync(new byte[] { 0xff });
    if (invalidStdoutBytes > 1)
    {
        await FloodAsync(stdout, invalidStdoutBytes - 1);
    }
    return 0;
}
if (TryGetByteCount(input, "INVALID_UTF8_STDERR:", out int invalidStderrBytes))
{
    Stream stderr = Console.OpenStandardError();
    await stderr.WriteAsync(new byte[] { 0xff });
    if (invalidStderrBytes > 1)
    {
        await FloodAsync(stderr, invalidStderrBytes - 1);
    }
    return 0;
}
if (string.Equals(input, "CLOSE_STDOUT", StringComparison.Ordinal))
{
    Console.OpenStandardOutput().Dispose();
    await Task.Delay(configuredDelay);
    return 0;
}
if (string.Equals(input, "CLOSE_STDERR", StringComparison.Ordinal))
{
    Console.OpenStandardError().Dispose();
    await Task.Delay(configuredDelay);
    await Console.Out.WriteAsync(prefix + input);
    return 0;
}

int delay = configuredDelay;
if (input.StartsWith("SLOW:", StringComparison.Ordinal))
{
    string rest = input[5..];
    int separator = rest.IndexOf('|');
    string delayText = separator < 0 ? rest : rest[..separator];
    delay = int.Parse(delayText);
    if (separator >= 0)
    {
        string marker = rest[(separator + 1)..];
        await File.AppendAllTextAsync(marker, "invoke\n");
    }
}

string? milestoneMarker = Get(args, "--marker");
string? releaseFile = Get(args, "--release-file");
if (string.Equals(mode, "milestone", StringComparison.Ordinal) && milestoneMarker is not null)
{
    await File.AppendAllTextAsync(milestoneMarker, "physical-side-effect\n");
    if (releaseFile is not null)
    {
        await WaitForFileAsync(releaseFile, TimeSpan.FromSeconds(30));
    }
}

if (delay > 0)
{
    await Task.Delay(delay);
}

await Console.Out.WriteAsync(prefix + input);
return 0;

static async Task FloodAsync(Stream stream, int byteCount)
{
    byte[] chunk = Enumerable.Repeat((byte)'x', 64 * 1024).ToArray();
    int remaining = byteCount;
    while (remaining > 0)
    {
        int count = Math.Min(chunk.Length, remaining);
        await stream.WriteAsync(chunk.AsMemory(0, count));
        remaining -= count;
    }
    await stream.FlushAsync();
}

static bool TryGetByteCount(string value, string prefix, out int byteCount)
{
    if (value.StartsWith(prefix, StringComparison.Ordinal)
        && int.TryParse(value[prefix.Length..], out byteCount)
        && byteCount > 0)
    {
        return true;
    }
    byteCount = 0;
    return false;
}

static async Task WaitForFileAsync(string path, TimeSpan timeout)
{
    Stopwatch stopwatch = Stopwatch.StartNew();
    while (stopwatch.Elapsed < timeout)
    {
        if (File.Exists(path))
        {
            return;
        }
        await Task.Delay(10);
    }
    throw new TimeoutException($"fixture file did not appear: {path}");
}

static string? Get(string[] values, string key)
{
    int index = Array.IndexOf(values, key);
    return index >= 0 && index + 1 < values.Length ? values[index + 1] : null;
}
