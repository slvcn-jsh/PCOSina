import asyncio
import sys
from pathlib import Path

from starlette.requests import Request
from starlette.responses import JSONResponse

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import main


def _request_without_content_length(chunks: list[bytes]) -> Request:
    messages = [
        {
            "type": "http.request",
            "body": chunk,
            "more_body": index < len(chunks) - 1,
        }
        for index, chunk in enumerate(chunks)
    ]

    async def receive():
        if messages:
            return messages.pop(0)
        return {"type": "http.request", "body": b"", "more_body": False}

    return Request(
        {
            "type": "http",
            "method": "POST",
            "path": "/feedback",
            "headers": [],
            "query_string": b"",
            "client": ("127.0.0.1", 12345),
            "server": ("testserver", 80),
            "scheme": "http",
        },
        receive,
    )


def _request_that_disconnects(method: str) -> Request:
    async def receive():
        return {"type": "http.disconnect"}

    return Request(
        {
            "type": "http",
            "method": method,
            "path": "/",
            "headers": [],
            "query_string": b"",
            "client": ("127.0.0.1", 12345),
            "server": ("testserver", 80),
            "scheme": "http",
        },
        receive,
    )


def test_request_size_limit_counts_streamed_body_without_content_length(monkeypatch):
    monkeypatch.setattr(main, "MAX_REQUEST_BYTES", 8)
    called = {"value": False}

    async def call_next(_request):
        called["value"] = True
        return JSONResponse({"status": "unexpected"})

    async def run():
        request = _request_without_content_length([b"1234", b"5678", b"9"])
        return await main.limit_request_size(request, call_next)

    response = asyncio.run(run())

    assert response.status_code == 413
    assert called["value"] is False


def test_request_size_limit_replays_accepted_body_without_content_length(monkeypatch):
    monkeypatch.setattr(main, "MAX_REQUEST_BYTES", 16)

    async def call_next(request):
        body = await request.body()
        return JSONResponse({"size": len(body), "body": body.decode("utf-8")})

    async def run():
        request = _request_without_content_length([b"hello", b"-ok"])
        return await main.limit_request_size(request, call_next)

    response = asyncio.run(run())

    assert response.status_code == 200
    assert response.body == b'{"size":8,"body":"hello-ok"}'


def test_request_size_limit_does_not_read_body_for_probe_methods():
    called_methods = []

    async def call_next(request):
        called_methods.append(request.method)
        return JSONResponse({"status": "ok"})

    async def run():
        return [
            await main.limit_request_size(_request_that_disconnects(method), call_next)
            for method in ("GET", "HEAD", "OPTIONS")
        ]

    responses = asyncio.run(run())

    assert [response.status_code for response in responses] == [200, 200, 200]
    assert called_methods == ["GET", "HEAD", "OPTIONS"]


def test_request_size_limit_treats_body_upload_disconnect_as_client_closed_request():
    called = {"value": False}

    async def call_next(_request):
        called["value"] = True
        return JSONResponse({"status": "unexpected"})

    async def run():
        return await main.limit_request_size(_request_that_disconnects("POST"), call_next)

    response = asyncio.run(run())

    assert response.status_code == 499
    assert called["value"] is False
