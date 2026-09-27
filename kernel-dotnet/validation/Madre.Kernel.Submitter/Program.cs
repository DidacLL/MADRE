using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using Madre.Kernel;

if (args.Length != 2)
{
    Console.Error.WriteLine("usage: Madre.Kernel.Submitter <base-uri> <prepared-input>");
    return 2;
}

var options = new JsonSerializerOptions(JsonSerializerDefaults.Web);
options.Converters.Add(new JsonStringEnumConverter());
using var client = new HttpClient { BaseAddress = new Uri(args[0]) };
var request = new PhysicalInferenceRequest(
    args[1],
    InferenceEffort.Standard,
    WorkUrgency.Normal,
    null,
    null,
    ExecutionBoundary.LocalOnly);
HttpResponseMessage response = await client.PostAsJsonAsync("/v1/work", request, options);
response.EnsureSuccessStatusCode();
WorkSubmissionResponse submission = (await response.Content.ReadFromJsonAsync<WorkSubmissionResponse>(options))!;
Console.WriteLine(submission.WorkId);
return 0;
