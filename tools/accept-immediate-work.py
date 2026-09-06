"""Independent-client acceptance check for real immediate inference through MADRE."""

import argparse
import json
import os
from urllib.error import HTTPError
from urllib.request import Request, urlopen


def request_json(url: str, token: str, *, body: dict[str, object] | None = None) -> dict:
    data = json.dumps(body).encode() if body is not None else None
    request = Request(
        url,
        data=data,
        headers={
            "Authorization": f"Bearer {token}",
            **({"Content-Type": "application/json"} if data is not None else {}),
        },
        method="POST" if data is not None else "GET",
    )
    with urlopen(request, timeout=180) as response:
        return json.load(response)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://127.0.0.1:8731")
    parser.add_argument("--capability", default="local-chat")
    parser.add_argument("--application-id", default="madre-http-acceptance")
    parser.add_argument("--prompt", default="Reply with a short greeting.")
    parser.add_argument("--token-env", default="MADRE_API_TOKEN")
    args = parser.parse_args()

    token = os.environ.get(args.token_env, "")
    if not token:
        raise SystemExit(f"required environment variable is unset: {args.token_env}")

    submission = {
        "application_id": args.application_id,
        "capability_id": args.capability,
        "input": {
            "messages": [{"role": "user", "content": args.prompt}],
            "max_tokens": 64,
        },
        "constraints": {"timeout_seconds": 120, "local_only": True},
    }
    try:
        work = request_json(f"{args.url}/v1/work", token, body=submission)
        inspected = request_json(f"{args.url}/v1/work/{work['id']}", token)
    except HTTPError as exc:
        detail = exc.read().decode(errors="replace")
        raise SystemExit(f"MADRE HTTP {exc.code}: {detail}") from exc

    if inspected != work:
        raise SystemExit("inspection did not reproduce the submitted work record")
    if work.get("status") != "succeeded":
        raise SystemExit(json.dumps(work, indent=2))
    result = work.get("result")
    if not isinstance(result, dict) or not str(result.get("text", "")).strip():
        raise SystemExit("real inference completed without generated text")

    print(json.dumps(work, indent=2))


if __name__ == "__main__":
    main()
