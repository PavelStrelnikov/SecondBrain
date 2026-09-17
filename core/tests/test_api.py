"""Интеграционные тесты: нужен Postgres. Пропускаются, если TEST_DATABASE_URL не задан.

Запуск: TEST_DATABASE_URL=postgresql+asyncpg://brain:x@localhost:5440/brain_test pytest
"""
import os
import uuid
from datetime import datetime, timezone

import pytest
from httpx import ASGITransport, AsyncClient

TEST_DB = os.environ.get("TEST_DATABASE_URL")
pytestmark = pytest.mark.skipif(not TEST_DB, reason="TEST_DATABASE_URL not set")


@pytest.fixture
async def client(monkeypatch):
    monkeypatch.setenv("DATABASE_URL", TEST_DB)
    monkeypatch.setenv("DEVICE_TOKEN", "t0k")
    monkeypatch.setenv("HEALTH_PASSWORD", "pw")
    from importlib import reload

    from app import config, db

    reload(config)
    reload(db)
    from app.db import Base, engine

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    from app.main import app

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://t") as c:
        yield c


def _event(key: str) -> dict:
    return {
        "event_key": key,
        "type": "call",
        "source": "android",
        "direction": "incoming",
        "occurred_at": datetime.now(timezone.utc).isoformat(),
        "payload": {"number": "+972501234567", "duration_s": 12},
    }


async def test_ingest_is_idempotent(client):
    key = f"k-{uuid.uuid4()}"
    h = {"Authorization": "Bearer t0k"}
    r1 = await client.post("/api/events", json={"events": [_event(key)]}, headers=h)
    r2 = await client.post("/api/events", json={"events": [_event(key)]}, headers=h)
    assert r1.status_code == 200 and r1.json()["accepted"] == 1
    assert r2.status_code == 200 and r2.json()["duplicates"] == 1


async def test_bad_token_rejected(client):
    r = await client.post("/api/events", json={"events": [_event("x")]}, headers={"Authorization": "Bearer nope"})
    assert r.status_code == 401


async def test_health_page_needs_password(client):
    assert (await client.get("/")).status_code == 401
    assert (await client.get("/", auth=("brain", "pw"))).status_code == 200
