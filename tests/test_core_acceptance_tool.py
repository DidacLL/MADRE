import importlib.util
import json
import sqlite3
from pathlib import Path
from types import ModuleType


def _load_tool() -> ModuleType:
    path = Path(__file__).parents[1] / "tools" / "core-acceptance.py"
    spec = importlib.util.spec_from_file_location("core_acceptance_tool", path)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_prepare_writes_dedicated_config_and_clears_data_dir(tmp_path, monkeypatch, capsys):
    tool = _load_tool()
    example = tmp_path / "madre.example.toml"
    config = tmp_path / "madre.acceptance.local.toml"
    data_dir = tmp_path / "dev" / "core-acceptance"
    example.write_text(
        '# data_dir = "./dev/runtime"\nhost = "127.0.0.1"\n',
        encoding="utf-8",
    )
    data_dir.mkdir(parents=True)
    (data_dir / "stale").write_text("old", encoding="utf-8")

    monkeypatch.setattr(tool, "ROOT", tmp_path)
    monkeypatch.setattr(tool, "EXAMPLE_CONFIG", example)
    monkeypatch.setattr(tool, "ACCEPTANCE_CONFIG", config)
    monkeypatch.setattr(tool, "ACCEPTANCE_DATA_DIR", data_dir)

    tool.prepare()

    assert config.read_text(encoding="utf-8").startswith('data_dir = "./dev/core-acceptance"\n')
    assert not data_dir.exists()
    assert "wrote madre.acceptance.local.toml" in capsys.readouterr().out


def test_status_reports_latest_core_work(tmp_path, monkeypatch, capsys):
    tool = _load_tool()
    data_dir = tmp_path / "dev" / "core-acceptance"
    database = data_dir / "runtime.sqlite3"
    data_dir.mkdir(parents=True)
    with sqlite3.connect(database) as connection:
        connection.execute(
            """
            CREATE TABLE runtime_work (
                id TEXT,
                application_id TEXT,
                status TEXT,
                input_json TEXT,
                submitted_at TEXT
            )
            """
        )
        connection.executemany(
            "INSERT INTO runtime_work VALUES (?, ?, ?, ?, ?)",
            [
                (
                    "work-1",
                    "madre-core",
                    "succeeded",
                    json.dumps({"max_tokens": 256}),
                    "2026-09-06T00:00:00+00:00",
                ),
                (
                    "work-2",
                    "madre-core",
                    "succeeded",
                    json.dumps({"max_tokens": 768}),
                    "2026-09-06T00:01:00+00:00",
                ),
            ],
        )

    monkeypatch.setattr(tool, "ACCEPTANCE_DB", database)
    tool.status()

    output = capsys.readouterr().out
    assert "count=2" in output
    assert "latest_id=work-2" in output
    assert "latest_status=succeeded" in output
    assert "latest_max_tokens=768" in output
