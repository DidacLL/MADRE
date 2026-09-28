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
                await RunRegressionVerificationAsync(options);
                await RunIntegrationVerificationAsync(options);
                await ProcessBindingIntegrationVerificationAsync();
                await RunCorrectedExpandedIntegrationVerificationAsync(options);
                await RunAdditionalBoundaryVerificationAsync();
                break;
            case "qualification":
                await RunRegressionVerificationAsync(options);
                await RunIntegrationVerificationAsync(options);
                await ProcessBindingIntegrationVerificationAsync();
                await RunCorrectedExpandedIntegrationVerificationAsync(options);
                await RunAdditionalBoundaryVerificationAsync();
                await RunQualificationStressAsync(options);
                await RunExpandedQualificationVerificationAsync(options);
                break;
            case "stress":
                await RunStressVerificationAsync(options);
                await RunExpandedStressVerificationAsync(options);
                break;
            case "soak":
                await RunQualifiedSoakVerificationAsync(options);
                break;
            default:
                throw new InvalidDataException($"unknown verification mode: {options.Mode}");
        }

        Console.WriteLine($"MADRE Kernel verification {options.Mode} passed seed={options.Seed}");
        return 0;
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
