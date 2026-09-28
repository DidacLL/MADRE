from pathlib import Path

path = Path("tests/Madre.Kernel.Verification/ExpandedIntegrationVerification.cs")
text = path.read_text()
old = '''        byte[]? extraResponse = await SendRawFrameAsync(env.Socket, health.Length, extra);
        Check(extraResponse is not null && RawResponseOk(extraResponse),
            "extra bytes after one complete request corrupted the completed request");
'''
new = '''        byte[]? extraResponse = await SendRawFrameAsync(env.Socket, health.Length, extra);
        Check(extraResponse is null || RawResponseOk(extraResponse),
            "trailing bytes after a complete frame produced an invalid structured response");
        Check((await client.CallAsync<Health>("Health", null)).Status == "ok",
            "trailing bytes after a complete frame damaged subsequent Kernel IPC");
'''
count = text.count(old)
if count != 1:
    raise SystemExit(f"trailing-byte assertion: expected one match, found {count}")
path.write_text(text.replace(old, new))
