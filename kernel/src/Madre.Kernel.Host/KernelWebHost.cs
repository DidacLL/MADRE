using System.Text.Json.Serialization;
using Madre.Kernel;

namespace Madre.Kernel.Host;

public static class KernelWebHost
{
    public static async Task RunAsync(
        string databasePath,
        int port,
        int maxConcurrent,
        IReadOnlyList<InferenceCapability> capabilities,
        IReadOnlyList<IInferenceBinding> bindings,
        CancellationToken cancellationToken = default)
    {
        var builder = WebApplication.CreateBuilder();
        builder.WebHost.ConfigureKestrel(options =>
        {
            options.AddServerHeader = false;
            options.Limits.MaxRequestBodySize = 1_100_000;
        });
        builder.Services.ConfigureHttpJsonOptions(options =>
            options.SerializerOptions.Converters.Add(new JsonStringEnumConverter()));

        WebApplication app = builder.Build();
        app.Urls.Add($"http://127.0.0.1:{port}");
        var engine = new KernelEngine(
            new WorkStore(databasePath),
            capabilities,
            bindings,
            maxConcurrent);
        await engine.InitializeAsync(cancellationToken).ConfigureAwait(false);
        engine.Start();

        app.MapGet("/health", () => Results.Ok(new { status = "ok" }));

        app.MapPost("/v1/work", async (PhysicalInferenceRequest request, CancellationToken ct) =>
        {
            try
            {
                string id = await engine.SubmitAsync(request, ct).ConfigureAwait(false);
                return Results.Ok(new WorkSubmissionResponse(id));
            }
            catch (ArgumentException ex)
            {
                return Results.BadRequest(new { error = ex.Message });
            }
        });

        app.MapGet("/v1/work/{id}/status", async (string id, CancellationToken ct) =>
        {
            WorkInspection? inspection = await engine.InspectAsync(id, ct).ConfigureAwait(false);
            return inspection is null
                ? Results.NotFound()
                : Results.Ok(new { inspection.WorkId, inspection.State, inspection.Released, inspection.FailureCode });
        });

        app.MapGet("/v1/work/{id}/inspect", async (string id, CancellationToken ct) =>
        {
            WorkInspection? inspection = await engine.InspectAsync(id, ct).ConfigureAwait(false);
            return inspection is null ? Results.NotFound() : Results.Ok(inspection);
        });

        app.MapGet("/v1/work/{id}/result", async (string id, CancellationToken ct) =>
        {
            WorkResultSnapshot? result = await engine.ResultAsync(id, ct).ConfigureAwait(false);
            if (result is null)
            {
                return Results.NotFound();
            }
            if (result.Released)
            {
                return Results.StatusCode(StatusCodes.Status410Gone);
            }
            if (result.State != WorkState.Succeeded)
            {
                return Results.Conflict(result);
            }
            return Results.Ok(result);
        });

        app.MapPost("/v1/work/{id}/cancel", async (string id, CancellationToken ct) =>
        {
            WorkState? state = await engine.CancelAsync(id, ct).ConfigureAwait(false);
            return state is null ? Results.NotFound() : Results.Ok(new { state });
        });

        app.MapPost("/v1/work/{id}/release", async (string id, CancellationToken ct) =>
        {
            bool? released = await engine.ReleaseAsync(id, ct).ConfigureAwait(false);
            return released switch
            {
                null => Results.NotFound(),
                false => Results.Conflict(new { error = "WORK_NOT_TERMINAL" }),
                true => Results.Ok(new { released = true })
            };
        });

        app.MapGet("/v1/capabilities", async (CancellationToken ct) =>
            Results.Ok(await engine.CapabilitiesAsync(ct).ConfigureAwait(false)));

        app.MapPost("/v1/capabilities/refresh", async (CancellationToken ct) =>
            Results.Ok(await engine.RefreshCapabilityStatesAsync(ct).ConfigureAwait(false)));

        try
        {
            await app.RunAsync(cancellationToken).ConfigureAwait(false);
        }
        finally
        {
            await engine.DisposeAsync().ConfigureAwait(false);
            await app.DisposeAsync().ConfigureAwait(false);
        }
    }
}
