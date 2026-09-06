import os
import sqlite3
import subprocess
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from madre import Settings, create_app, load_settings
from madre.contracts import ExecutionConstraints, WorkSubmission
from madre.storage import STORAGE_FORMAT_ID, open_database


def test_config_paths_and_validation(tmp_path, monkeypatch):
    monkeypatch.delenv("MADRE_API_TOKEN", raising=False)
    config = tmp_path / "madre.toml"
    config.write_text('data_dir = "runtime"\n', encoding="utf-8")
    monkeypatch.chdir(tmp_path.parent)
    settings = load_settings(config)
    assert settings.data_dir == tmp_path / "runtime"
    assert not settings.data_dir.exists()
    with pytest.raises(ValueError, match="credential"):
        settings.token()
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    assert settings.token() == "test-token"
    for content in ('host="0.0.0.0"', "unexpected=true", "port=0"):
        config.write_text(content, encoding="utf-8")
        with pytest.raises(ValidationError):
            load_settings(config)


def test_import_has_no_filesystem_side_effects(tmp_path):
    environment = dict(os.environ, LOCALAPPDATA=str(tmp_path), XDG_DATA_HOME=str(tmp_path))
    result = subprocess.run(
        [sys.executable, "-B", "-c", "import madre; print(madre.__name__)"],
        env=environment,
        cwd=tmp_path,
        capture_output=True,
        text=True,
        check=True,
    )
    assert result.stdout.strip() == "madre"
    assert list(tmp_path.iterdir()) == []


def test_exclusive_database_reopen_and_discards_incompatible_development_storage(tmp_path):
    with open_database(tmp_path) as connection:
        assert connection.execute("PRAGMA user_version").fetchone()[0] == STORAGE_FORMAT_ID
        with pytest.raises(RuntimeError, match="another MADRE"):
            with open_database(tmp_path):
                pytest.fail("second owner accepted")
    with open_database(tmp_path) as connection:
        assert connection.execute("PRAGMA foreign_keys").fetchone()[0] == 1

    stale = tmp_path / "stale"
    stale.mkdir()
    stale_format_id = STORAGE_FORMAT_ID + 1
    with sqlite3.connect(stale / "runtime.sqlite3") as connection:
        connection.execute("CREATE TABLE obsolete_state (value TEXT)")
        connection.execute("INSERT INTO obsolete_state VALUES ('discard me')")
        connection.execute(f"PRAGMA user_version={stale_format_id}")

    with open_database(stale) as connection:
        assert connection.execute("PRAGMA user_version").fetchone()[0] == STORAGE_FORMAT_ID
        assert (
            connection.execute(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='obsolete_state'"
            ).fetchone()
            is None
        )
        assert (
            connection.execute(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='runtime_work'"
            ).fetchone()[0]
            == "runtime_work"
        )


def test_process_exit_releases_ownership(tmp_path):
    script = (
        "from madre.storage import open_database; from pathlib import Path; import sys; "
        "owner=open_database(Path(sys.argv[1])); owner.__enter__(); "
        "print('ready', flush=True); sys.stdin.read()"
    )
    process = subprocess.Popen(
        [sys.executable, "-c", script, str(tmp_path)],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    try:
        assert process.stdout.readline().strip() == "ready"
        with pytest.raises(RuntimeError, match="another MADRE"):
            with open_database(tmp_path):
                pytest.fail("second process accepted")
        process.kill()
        process.wait(timeout=10)
        with open_database(tmp_path):
            pass
    finally:
        if process.poll() is None:
            process.kill()
        process.communicate(timeout=10)


def test_health_auth_and_lifecycle(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    settings = Settings(data_dir=tmp_path / "runtime")
    app = create_app(settings)
    assert not settings.data_dir.exists()
    with TestClient(app) as client:
        assert client.get("/health").status_code == 401
        assert client.get("/health", headers={"Authorization": "Bearer wrong"}).status_code == 401
        response = client.get("/health", headers={"Authorization": "Bearer test-token"})
        assert response.json() == {"status": "ok"}
        assert "access-control-allow-origin" not in response.headers
        with pytest.raises(RuntimeError, match="another MADRE"):
            with open_database(settings.data_dir):
                pytest.fail("service did not own storage")
    with open_database(settings.data_dir):
        pass


def test_contract_rejects_naive_time_and_unknown_constraints():
    base = dict(application_id="app", capability_id="chat", input={})
    work = WorkSubmission(**base, eligible_at="2026-09-06T12:00:00+02:00")
    assert work.eligible_at.isoformat() == "2026-09-06T10:00:00+00:00"
    with pytest.raises(ValidationError):
        WorkSubmission(**base, eligible_at="2026-09-06T12:00:00")
    with pytest.raises(ValidationError):
        ExecutionConstraints(money_budget=10)
    with pytest.raises(ValidationError):
        ExecutionConstraints(timeout_seconds=float("nan"))


def test_cli_failure_is_nonzero_and_structured(tmp_path):
    config = tmp_path / "madre.toml"
    config.write_text("", encoding="utf-8")
    environment = dict(os.environ)
    environment.pop("MADRE_API_TOKEN", None)
    executable = Path(sys.executable).parent / ("madre.exe" if os.name == "nt" else "madre")
    result = subprocess.run(
        [str(executable), "--config", str(config), "check-config"],
        env=environment,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 1
    assert '"error": "configuration_or_runtime"' in result.stdout
