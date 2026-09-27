string input = await Console.In.ReadToEndAsync();
int delayMs = 220;
string? marker = null;

if (input.StartsWith("SLOW:", StringComparison.Ordinal))
{
    string[] parts = input.Split('|', 2);
    if (int.TryParse(parts[0].AsSpan("SLOW:".Length), out int parsed))
    {
        delayMs = parsed;
    }
    if (parts.Length == 2 && parts[1].Length > 0)
    {
        marker = parts[1];
    }
}

if (marker is not null)
{
    Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(marker))!);
    await File.AppendAllTextAsync(marker, "invoked\n");
}

await Task.Delay(delayMs);
if (input.StartsWith("FAIL", StringComparison.Ordinal))
{
    return 17;
}

await Console.Out.WriteAsync(input);
return 0;
