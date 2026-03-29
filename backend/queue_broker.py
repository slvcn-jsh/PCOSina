from __future__ import annotations

import os
import threading
from collections import deque
from typing import Any, Optional, Protocol


class QueueBroker(Protocol):
    backend: str

    def is_enabled(self) -> bool: ...

    def publish(self, job_id: str) -> bool: ...

    def pop(self, timeout_seconds: float = 1.0) -> Optional[str]: ...

    def health(self) -> dict[str, Any]: ...


class _DbQueueBroker:
    backend = "db"

    def is_enabled(self) -> bool:
        return False

    def publish(self, job_id: str) -> bool:
        return False

    def pop(self, timeout_seconds: float = 1.0) -> Optional[str]:
        return None

    def health(self) -> dict[str, Any]:
        return {"backend": self.backend, "enabled": False}


class _MemoryQueueBroker:
    backend = "memory"
    _queue: deque[str] = deque()
    _lock = threading.Lock()

    def is_enabled(self) -> bool:
        return True

    def publish(self, job_id: str) -> bool:
        token = str(job_id or "").strip()
        if not token:
            return False
        with self._lock:
            self._queue.append(token)
        return True

    def pop(self, timeout_seconds: float = 1.0) -> Optional[str]:
        with self._lock:
            if not self._queue:
                return None
            return self._queue.popleft()

    def health(self) -> dict[str, Any]:
        with self._lock:
            depth = len(self._queue)
        return {"backend": self.backend, "enabled": True, "queueDepth": depth}


class _RedisQueueBroker:
    backend = "redis"

    def __init__(self, redis_url: str, queue_key: str):
        self._redis_url = redis_url.strip()
        self._queue_key = queue_key.strip() or "pcosina:plan_jobs"
        self._client: Any = None
        self._load_error: str = ""
        self._connect()

    def _connect(self) -> None:
        if not self._redis_url:
            self._load_error = "missing_redis_url"
            self._client = None
            return
        try:
            import redis  # type: ignore

            self._client = redis.Redis.from_url(self._redis_url, decode_responses=True)
            # Fast ping at init so health() is meaningful.
            self._client.ping()
        except Exception as exc:
            self._client = None
            self._load_error = str(exc)

    def is_enabled(self) -> bool:
        return self._client is not None

    def publish(self, job_id: str) -> bool:
        token = str(job_id or "").strip()
        if not token or self._client is None:
            return False
        try:
            self._client.rpush(self._queue_key, token)
            return True
        except Exception:
            return False

    def pop(self, timeout_seconds: float = 1.0) -> Optional[str]:
        if self._client is None:
            return None
        timeout = max(0, int(timeout_seconds))
        try:
            item = self._client.blpop(self._queue_key, timeout=timeout)
        except Exception:
            return None
        if not item:
            return None
        if isinstance(item, (list, tuple)) and len(item) >= 2:
            value = item[1]
        else:
            value = item
        text = str(value or "").strip()
        return text or None

    def health(self) -> dict[str, Any]:
        if self._client is None:
            return {
                "backend": self.backend,
                "enabled": False,
                "error": self._load_error or "redis_unavailable",
            }
        depth = None
        ping_ok = False
        err = ""
        try:
            ping_ok = bool(self._client.ping())
            depth = int(self._client.llen(self._queue_key))
        except Exception as exc:
            err = str(exc)
        return {
            "backend": self.backend,
            "enabled": ping_ok and not err,
            "queueDepth": depth,
            "error": err or None,
            "queueKey": self._queue_key,
        }


def build_broker_from_env() -> QueueBroker:
    mode = os.getenv("PCOSINA_QUEUE_BACKEND", "db").strip().lower()
    if mode in ("", "db", "database", "none", "off", "disabled"):
        return _DbQueueBroker()
    if mode in ("memory", "mem", "inmemory"):
        return _MemoryQueueBroker()
    if mode in ("redis", "broker", "broker_redis"):
        return _RedisQueueBroker(
            redis_url=os.getenv("PCOSINA_REDIS_URL", ""),
            queue_key=os.getenv("PCOSINA_REDIS_QUEUE_KEY", "pcosina:plan_jobs"),
        )
    # Unknown mode fails closed to DB queue semantics.
    return _DbQueueBroker()

