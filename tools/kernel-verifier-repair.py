from pathlib import Path

path = Path("tests/Madre.Kernel.Verification/ProcessIntegrationVerification.cs")
text = path.read_text()
old = '''        await WaitFileAsync(childMarker);
        int childPid = int.Parse((await File.ReadAllTextAsync(childMarker)).Trim());
'''
new = '''        int childPid = 0;
        await WaitUntilAsync(
            () =>
            {
                try
                {
                    return File.Exists(childMarker)
                        && int.TryParse(File.ReadAllText(childMarker).Trim(), out childPid);
                }
                catch (IOException)
                {
                    return false;
                }
            },
            5_000,
            "child process marker did not contain a parseable PID");
'''
count = text.count(old)
if count != 1:
    raise SystemExit(f"child PID marker: expected one match, found {count}")
path.write_text(text.replace(old, new))
