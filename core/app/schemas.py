from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, Field

EventType = Literal["call", "message", "notification", "photo", "location", "app_state"]
Direction = Literal["incoming", "outgoing", "missed", "rejected", "blocked"]


class EventIn(BaseModel):
    event_key: str = Field(min_length=1, max_length=200)
    type: EventType
    source: str = Field(min_length=1, max_length=40)
    direction: Direction | None = None
    occurred_at: datetime
    device_id: str | None = Field(default=None, max_length=80)
    payload: dict[str, Any] = Field(default_factory=dict)


class EventBatchIn(BaseModel):
    events: list[EventIn] = Field(min_length=1, max_length=500)


class EventBatchOut(BaseModel):
    accepted: int
    duplicates: int
    server_time: datetime


class EventOut(BaseModel):
    event_key: str
    type: str
    source: str
    direction: str | None
    occurred_at: datetime
    received_at: datetime
    device_id: str | None
    payload: dict[str, Any]

    model_config = {"from_attributes": True}
