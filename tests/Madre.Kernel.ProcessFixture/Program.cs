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

int configuredDelay = int.Parse(Get(args, "--delay-ms") ?? "0");
string prefix = Get(args, "--prefix") ?? string.Empty;
string input = await Console.In.ReadToEndAsync();

if (string.Equals(input, "FAIL", StringComparison.Ordinal))
{
    return 17;
}

if (TryGetByteCount(input, "FLOOD_STDOUT:", out int stdoutBytes))
{
    await FloodAsync(Console.OpenStandardOutput(), stdoutBytes);
    return 0;
}

if (TryGetByteCount(input, "INVALID_UTF8_STDERR:", out int stderrBytes))
{
    Stream stderr = Console.OpenStandardError();
    await stderr.WriteAsync(new byte[] { 0xff });
    await FloodAsync(stderr, stderrBytes);
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

static string? Get(string[] values, string key)
{
    int index = Array.IndexOf(values, key);
    return index >= 0 && index + 1 < values.Length ? values[index + 1] : null;
}
