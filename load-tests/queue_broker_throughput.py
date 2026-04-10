#!/usr/bin/env python
from __future__ import annotations

import argparse
import json
import os
import uuid
import threading
import time
from pathlib import Path

import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT / "backend") not in sys.path:
    sys.path.insert(0, str(ROOT / "backend"))

import queue_broker  # type: ignore


def main() -> int:
    parser = argparse.ArgumentParser(description="Queue broker throughput/load probe.")
    parser.add_argument("--backend", choices=["memory", "redis"], default="memory")
    parser.add_argument("--jobs", type=int, default=1000)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument(
        "--queue-key",
        default="",
        help="Optional Redis queue key override (defaults to a per-run unique key for redis backend).",
    )
    parser.add_argument(
        "--consume-timeout-seconds",
        type=float,
        default=120.0,
        help="Upper bound wait time for consumers to drain expected probe jobs.",
    )
    parser.add_argument("--output", default="benchmarks/reports/queue_broker_throughput.json")
    args = parser.parse_args()

    jobs = max(1, int(args.jobs))
    workers = max(1, int(args.workers))
    os.environ["PCOSINA_QUEUE_BACKEND"] = args.backend
    if args.backend == "redis":
        queue_key = str(args.queue_key or "").strip() or f"pcosina:plan_jobs:probe:{uuid.uuid4().hex}"
        os.environ["PCOSINA_REDIS_QUEUE_KEY"] = queue_key
    else:
        queue_key = ""
    broker = queue_broker.build_broker_from_env()
    if not broker.is_enabled():
        print(f"broker backend not enabled: {broker.health()}")
        return 2

    run_id = uuid.uuid4().hex[:10]
    published_ids = [f"probe-{run_id}-job-{i}" for i in range(jobs)]
    expected_ids = set(published_ids)
    publish_start = time.perf_counter()
    publish_ok = 0
    for job_id in published_ids:
        if broker.publish(job_id):
            publish_ok += 1
    publish_seconds = max(1e-9, time.perf_counter() - publish_start)

    popped_lock = threading.Lock()
    popped_ids: list[str] = []
    seen_ids: set[str] = set()
    consume_deadline = time.perf_counter() + max(1.0, float(args.consume_timeout_seconds))

    def _worker():
        while True:
            with popped_lock:
                if len(seen_ids) >= jobs:
                    return
            if time.perf_counter() >= consume_deadline:
                return
            item = broker.pop(timeout_seconds=1.0)
            if item is None:
                continue
            token = str(item)
            if token not in expected_ids:
                # Ignore foreign/stale queue items from other workloads.
                continue
            with popped_lock:
                if token not in seen_ids:
                    seen_ids.add(token)
                    popped_ids.append(token)

    pop_start = time.perf_counter()
    threads = [threading.Thread(target=_worker, daemon=True) for _ in range(workers)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join(timeout=max(1.0, float(args.consume_timeout_seconds)))
    pop_seconds = max(1e-9, time.perf_counter() - pop_start)

    unique_popped = len(set(popped_ids))
    success = publish_ok == jobs and unique_popped == jobs
    payload = {
        "backend": args.backend,
        "queueKey": queue_key or None,
        "runId": run_id,
        "jobs": jobs,
        "workers": workers,
        "publish": {
            "okCount": publish_ok,
            "seconds": publish_seconds,
            "throughputPerSec": publish_ok / publish_seconds,
        },
        "consume": {
            "popCount": len(popped_ids),
            "uniquePopCount": unique_popped,
            "missingCount": max(0, jobs - unique_popped),
            "seconds": pop_seconds,
            "throughputPerSec": len(popped_ids) / pop_seconds,
        },
        "success": success,
        "brokerHealth": broker.health(),
        "generatedAtMs": int(time.time() * 1000),
    }

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")
    print(f"QUEUE BROKER THROUGHPUT REPORT WRITTEN: {out}")
    return 0 if success else 1


if __name__ == "__main__":
    raise SystemExit(main())
