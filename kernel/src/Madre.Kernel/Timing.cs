namespace Madre.Kernel;

public interface IKernelClock
{
    DateTimeOffset UtcNow { get; }
    Task DelayAsync(TimeSpan delay, CancellationToken cancellationToken);
}

public sealed class SystemKernelClock : IKernelClock
{
    public static SystemKernelClock Instance { get; } = new();

    private SystemKernelClock()
    {
    }

    public DateTimeOffset UtcNow => DateTimeOffset.UtcNow;

    public Task DelayAsync(TimeSpan delay, CancellationToken cancellationToken) =>
        Task.Delay(delay, cancellationToken);
}

public sealed record KernelTimingOptions(
    TimeSpan ProbeTimeout,
    TimeSpan UnavailableReobserveInterval)
{
    public static KernelTimingOptions Default { get; } = new(
        TimeSpan.FromSeconds(3),
        TimeSpan.FromSeconds(1));
}
