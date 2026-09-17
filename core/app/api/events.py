from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Query
from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth import require_device, require_health_user
from app.db import get_db
from app.models import Event
from app.schemas import EventBatchIn, EventBatchOut, EventOut

router = APIRouter(prefix="/api/events", tags=["events"])


@router.post("", response_model=EventBatchOut)
async def ingest_events(
    batch: EventBatchIn,
    device_id: str = Depends(require_device),
    db: AsyncSession = Depends(get_db),
) -> EventBatchOut:
    """Приём пакета событий с телефона. Повтор по event_key молча игнорируется (идемпотентность)."""
    rows = [
        {
            "event_key": e.event_key,
            "type": e.type,
            "source": e.source,
            "direction": e.direction,
            "occurred_at": e.occurred_at,
            "device_id": e.device_id or device_id,
            "payload": e.payload,
        }
        for e in batch.events
    ]
    stmt = insert(Event).values(rows).on_conflict_do_nothing(index_elements=["event_key"]).returning(Event.id)
    result = await db.execute(stmt)
    inserted = len(result.fetchall())
    await db.commit()
    return EventBatchOut(
        accepted=inserted,
        duplicates=len(rows) - inserted,
        server_time=datetime.now(timezone.utc),
    )


@router.get("", response_model=list[EventOut])
async def list_events(
    limit: int = Query(default=100, ge=1, le=1000),
    type: str | None = None,
    since: datetime | None = None,
    _: str = Depends(require_health_user),
    db: AsyncSession = Depends(get_db),
) -> list[Event]:
    stmt = select(Event).order_by(Event.occurred_at.desc()).limit(limit)
    if type:
        stmt = stmt.where(Event.type == type)
    if since:
        stmt = stmt.where(Event.occurred_at >= since)
    return list((await db.execute(stmt)).scalars().all())
