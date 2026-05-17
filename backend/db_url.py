from __future__ import annotations

from urllib.parse import urlparse


POSTGRES_SCHEMES = {"postgres", "postgresql"}


def is_postgres_database_url(database_url: str | None) -> bool:
    """Return true only for real PostgreSQL connection-string schemes."""
    scheme = urlparse(str(database_url or "").strip()).scheme.lower()
    return scheme in POSTGRES_SCHEMES

