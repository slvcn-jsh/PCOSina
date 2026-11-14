#!/usr/bin/env python
"""Postgres backup/restore helper for production rollback drills.

Requires pg_dump and pg_restore from PostgreSQL client tools.
"""

from __future__ import annotations

import argparse
import os
import shlex
import shutil
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit


def mask_database_url(database_url: str) -> str:
    parsed = urlsplit(database_url)
    if not parsed.scheme or not parsed.netloc:
        return "<invalid-database-url>"
    host = parsed.hostname or ""
    port = f":{parsed.port}" if parsed.port else ""
    user = parsed.username or ""
    auth = f"{user}:***@" if user else ""
    return urlunsplit((parsed.scheme, f"{auth}{host}{port}", parsed.path, parsed.query, parsed.fragment))


def resolve_database_url(value: str | None) -> str:
    database_url = (value or os.getenv("DATABASE_URL") or "").strip()
    if not database_url:
        raise SystemExit("DATABASE_URL is required. Pass --database-url or set DATABASE_URL.")
    if not database_url.startswith(("postgres://", "postgresql://")):
        raise SystemExit("DATABASE_URL must use postgres:// or postgresql://.")
    return database_url


def require_tool(name: str) -> str:
    tool = shutil.which(name)
    if not tool:
        raise SystemExit(f"{name} was not found on PATH. Install PostgreSQL client tools first.")
    return tool


def backup_command(pg_dump: str, database_url: str, output: Path) -> list[str]:
    return [
        pg_dump,
        "--format=custom",
        "--no-owner",
        "--no-privileges",
        "--file",
        str(output),
        database_url,
    ]


def restore_command(pg_restore: str, database_url: str, input_file: Path) -> list[str]:
    return [
        pg_restore,
        "--clean",
        "--if-exists",
        "--no-owner",
        "--no-privileges",
        "--dbname",
        database_url,
        str(input_file),
    ]


def masked_command(command: list[str], database_url: str) -> str:
    return shlex.join([mask_database_url(database_url) if arg == database_url else arg for arg in command])


def run(command: list[str], *, database_url: str, dry_run: bool) -> int:
    if dry_run:
        print(masked_command(command, database_url))
        return 0
    completed = subprocess.run(command, check=False)
    return int(completed.returncode)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Backup or restore the PCOSina Postgres database.")
    parser.add_argument("--database-url", help="Postgres connection URL. Defaults to DATABASE_URL.")
    parser.add_argument("--dry-run", action="store_true", help="Print the command with the password masked.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    backup = subparsers.add_parser("backup", help="Create a custom-format pg_dump backup.")
    backup.add_argument("--output", required=True, type=Path, help="Output .dump path.")

    restore = subparsers.add_parser("restore", help="Restore a custom-format dump with pg_restore.")
    restore.add_argument("--input", required=True, type=Path, help="Input .dump path.")
    restore.add_argument(
        "--confirm-restore",
        required=True,
        help='Must be exactly "RESTORE" because restore uses --clean --if-exists.',
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    database_url = resolve_database_url(args.database_url)
    if args.command == "backup":
        output = args.output
        if output.exists() and not args.dry_run:
            raise SystemExit(f"Refusing to overwrite existing backup: {output}")
        output.parent.mkdir(parents=True, exist_ok=True)
        return run(backup_command(require_tool("pg_dump"), database_url, output), database_url=database_url, dry_run=args.dry_run)
    if args.command == "restore":
        if args.confirm_restore != "RESTORE":
            raise SystemExit('Restore requires --confirm-restore RESTORE.')
        input_file = args.input
        if not input_file.exists() and not args.dry_run:
            raise SystemExit(f"Backup file does not exist: {input_file}")
        return run(restore_command(require_tool("pg_restore"), database_url, input_file), database_url=database_url, dry_run=args.dry_run)
    raise SystemExit(f"Unsupported command: {args.command}")


if __name__ == "__main__":
    sys.exit(main())
