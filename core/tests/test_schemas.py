from datetime import datetime, timezone

import pytest
from pydantic import ValidationError

from app.schemas import EventBatchIn, EventIn


def test_event_minimal():
    e = EventIn(event_key="k1", type="call", source="android", occurred_at=datetime.now(timezone.utc))
    assert e.payload == {}
    assert e.direction is None


def test_event_rejects_unknown_type():
    with pytest.raises(ValidationError):
        EventIn(event_key="k1", type="sms", source="android", occurred_at=datetime.now(timezone.utc))


def test_batch_requires_at_least_one():
    with pytest.raises(ValidationError):
        EventBatchIn(events=[])
