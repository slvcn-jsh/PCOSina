import importlib.util
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "postgres_backup_restore.py"


def load_script_module():
    spec = importlib.util.spec_from_file_location("postgres_backup_restore", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def test_backup_dry_run_masks_database_password(monkeypatch, tmp_path, capsys):
    module = load_script_module()
    monkeypatch.setenv("DATABASE_URL", "postgresql://alice:secret@example.com:5432/pcosina")
    monkeypatch.setattr(module, "require_tool", lambda name: name)

    exit_code = module.main(["--dry-run", "backup", "--output", str(tmp_path / "pcosina.dump")])

    assert exit_code == 0
    output = capsys.readouterr().out
    assert "alice:***@example.com:5432/pcosina" in output
    assert "secret" not in output
    assert "pg_dump" in output


def test_restore_requires_explicit_confirmation(tmp_path):
    module = load_script_module()

    with pytest.raises(SystemExit, match="Restore requires"):
        module.main([
            "--database-url",
            "postgresql://alice:secret@example.com/pcosina",
            "--dry-run",
            "restore",
            "--input",
            str(tmp_path / "pcosina.dump"),
            "--confirm-restore",
            "NO",
        ])


def test_restore_dry_run_uses_clean_restore_command(monkeypatch, tmp_path, capsys):
    module = load_script_module()
    monkeypatch.setattr(module, "require_tool", lambda name: name)

    exit_code = module.main([
        "--database-url",
        "postgresql://alice:secret@example.com/pcosina",
        "--dry-run",
        "restore",
        "--input",
        str(tmp_path / "pcosina.dump"),
        "--confirm-restore",
        "RESTORE",
    ])

    assert exit_code == 0
    output = capsys.readouterr().out
    assert "pg_restore" in output
    assert "--clean" in output
    assert "--if-exists" in output
    assert "secret" not in output
