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
    TimeSpan UnavailableReobserveInterval,
    TimeSpan UnknownReobserveInterval,
    TimeSpan AvailableReobserveInterval)
{
    public static KernelTimingOptions Default { get; } = new(
        TimeSpan.FromSeconds(1),
        TimeSpan.FromSeconds(10),
        TimeSpan.FromSeconds(30));
}
