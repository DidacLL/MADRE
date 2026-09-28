from pathlib import Path

path = Path("tests/Madre.Kernel.Verification/StressVerification.cs")
text = path.read_text()
old = '''clock.Advance(TimeSpan.FromMinutes(1)); await WaitAllTerminalAsync(qe, future, Math.Max(60000, queueCount * 10)); Check((await InspectAllAsync(qe, future)).All(w => w.State == WorkState.Succeeded), "eligible queue stranded Work");'''
new = '''clock.Advance(TimeSpan.FromMinutes(1)); await WaitUntilAsync(() => qb.ExecutionCount == queueCount && qb.Active == 0, Math.Max(300_000, queueCount * 60), $"eligible queue physical execution did not drain; executed={qb.ExecutionCount}/{queueCount}, active={qb.Active}"); IReadOnlyList<WorkInspection> queueWorks = await InspectAllAsync(qe, future); string[] pendingQueue = queueWorks.Where(w => !IsTerminal(w.State)).Select(w => w.WorkId).ToArray(); if (pendingQueue.Length > 0) { await WaitAllTerminalAsync(qe, pendingQueue, 60_000); queueWorks = await InspectAllAsync(qe, future); } Check(queueWorks.All(w => w.State == WorkState.Succeeded), "eligible queue stranded Work");'''
count = text.count(old)
if count != 1:
    raise SystemExit(f"10k queue observation: expected one match, found {count}")
path.write_text(text.replace(old, new))
