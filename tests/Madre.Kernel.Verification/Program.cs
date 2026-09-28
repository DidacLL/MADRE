using System.Diagnostics;
using System.Globalization;

internal static partial class Program
{
    private sealed record VerificationOptions(
        string Mode,
        int Seed,
        VerificationScale Scale,
        TimeSpan SoakDuration);

    private enum VerificationScale
    {
        Small,
        Medium,
        Large
    }

    private static async Task<int> Main(string[] args)
    {
        InitPaths();
        VerificationOptions options = ParseVerificationOptions(args);
        Console.WriteLine($"MADRE Kernel verification mode={options.Mode} seed={options.Seed} scale={options.Scale} soak={options.SoakDuration}");

        switch (options.Mode)
        {
            case "regression":
                await RunPhaseAsync("regression-contracts", () => RunRegressionVerificationAsync(options));
                await RunPhaseAsync("integration", () => RunIntegrationVerificationAsync(options));
                await RunPhaseAsync("process-binding-integration", ProcessBindingIntegrationVerificationAsync);
                await RunPhaseAsync("expanded-integration", () => RunExpandedIntegrationVerificationAsync(options));
                await RunPhaseAsync("urgency-final-slot", UrgencyOnFinalSlotAsync, TimeSpan.FromMinutes(1));
                await RunPhaseAsync("java-stalled-peer-timeout", JavaStalledPeerTimeoutAsync, TimeSpan.FromMinutes(1));
                break;
            case "qualification":
                await RunPhaseAsync("regression-contracts", () => RunRegressionVerificationAsync(options));
                await RunPhaseAsync("integration", () => RunIntegrationVerificationAsync(options));
                await RunPhaseAsync("process-binding-integration", ProcessBindingIntegrationVerificationAsync);
                await RunPhaseAsync("expanded-integration", () => RunExpandedIntegrationVerificationAsync(options));
                await RunPhaseAsync("urgency-final-slot", UrgencyOnFinalSlotAsync, TimeSpan.FromMinutes(1));
                await RunPhaseAsync("java-stalled-peer-timeout", JavaStalledPeerTimeoutAsync, TimeSpan.FromMinutes(1));
                await RunPhaseAsync("qualification-stress", () => RunQualificationStressAsync(options));
                await RunPhaseAsync("expanded-qualification", () => RunExpandedQualificationVerificationAsync(options));
                break;
            case "stress":
                await RunPhaseAsync("stress", () => RunStressVerificationAsync(options));
                await RunPhaseAsync("expanded-stress", () => RunExpandedStressVerificationAsync(options));
                break;
            case "soak":
                await RunPhaseAsync(
                    "soak",
                    () => RunSoakVerificationAsync(options),
                    options.SoakDuration + TimeSpan.FromMinutes(2));
                break;
            default:
                throw new InvalidDataException($"unknown verification mode: {options.Mode}");
        }

        Console.WriteLine($"MADRE Kernel verification {options.Mode} passed seed={options.Seed}");
        return 0;
    }

    private static async Task RunPhaseAsync(
        string name,
        Func<Task> phase,
        TimeSpan? timeout = null)
    {
        TimeSpan limit = timeout ?? TimeSpan.FromMinutes(10);
        Stopwatch stopwatch = Stopwatch.StartNew();
        Console.WriteLine($"BEGIN phase={name} timeout={limit}");
        try
        {
            await phase().WaitAsync(limit).ConfigureAwait(false);
        }
        catch (TimeoutException ex)
        {
            throw new TimeoutException(
                $"verification phase '{name}' exceeded bounded runtime {limit}",
                ex);
        }
        Console.WriteLine($"PASS phase={name} elapsed={stopwatch.Elapsed}");
    }

    private static VerificationOptions ParseVerificationOptions(string[] args)
    {
        string mode = "regression";
        int seed = 0x00C0FFEE;
        VerificationScale scale = VerificationScale.Medium;
        TimeSpan soak = TimeSpan.FromSeconds(30);

        for (int i = 0; i < args.Length; i++)
        {
            string arg = args[i];
            if (i + 1 >= args.Length)
            {
                throw new InvalidDataException($"verification argument {arg} requires a value");
            }
            string value = args[++i];
            switch (arg)
            {
                case "--mode":
                    mode = value.ToLowerInvariant();
                    break;
                case "--seed":
                    if (!int.TryParse(value, NumberStyles.Integer, CultureInfo.InvariantCulture, out seed))
                    {
                        throw new InvalidDataException("--seed requires a 32-bit integer");
                    }
                    break;
                case "--scale":
                    scale = value.ToLowerInvariant() switch
                    {
                        "small" => VerificationScale.Small,
                        "medium" => VerificationScale.Medium,
                        "large" => VerificationScale.Large,
                        _ => throw new InvalidDataException("--scale must be small, medium or large")
                    };
                    break;
                case "--soak-seconds":
                    if (!int.TryParse(value, NumberStyles.None, CultureInfo.InvariantCulture, out int seconds) || seconds < 1)
                    {
                        throw new InvalidDataException("--soak-seconds requires a positive integer");
                    }
                    soak = TimeSpan.FromSeconds(seconds);
                    break;
                default:
                    throw new InvalidDataException($"unknown verification argument: {arg}");
            }
        }

        return new VerificationOptions(mode, seed, scale, soak);
    }

    private static int ScaleWorkCount(VerificationScale scale) => scale switch
    {
        VerificationScale.Small => 100,
        VerificationScale.Medium => 1_000,
        VerificationScale.Large => 10_000,
        _ => throw new ArgumentOutOfRangeException(nameof(scale))
    };
}
