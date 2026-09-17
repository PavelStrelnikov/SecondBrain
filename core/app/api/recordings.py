import json
import os
import uuid
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, File, Form, Query, UploadFile
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth import require_device, require_health_user
from app.config import settings
from app.db import get_db
from app.models import Event, Recording
from app.recordings_util import phone_from_filename, phones_match

router = APIRouter(prefix="/api/recordings", tags=["recordings"])

_MATCH_WINDOW = timedelta(hours=6)


@router.post("")
async def upload_recording(
    file: UploadFile = File(...),
    file_key: str = Form(...),
    recorded_at: datetime = Form(...),
    duration_s: int | None = Form(default=None),
    device_id: str = Depends(require_device),
    db: AsyncSession = Depends(get_db),
) -> dict:
    """Приём аудиофайла записи звонка. Идемпотентно по file_key (имя файла на телефоне)."""
    existing = (await db.execute(select(Recording).where(Recording.file_key == file_key))).scalar_one_or_none()
    if existing is not None:
        return {"status": "duplicate", "id": str(existing.id)}

    os.makedirs(settings.audio_dir, exist_ok=True)
    rec_id = uuid.uuid4()
    safe = "".join(c for c in os.path.basename(file.filename or "rec") if c.isalnum() or c in "._-")
    path = os.path.join(settings.audio_dir, f"{rec_id}_{safe}")
    data = await file.read()
    with open(path, "wb") as f:
        f.write(data)

    phone = phone_from_filename(file.filename or file_key)
    rec = Recording(
        id=rec_id,
        file_key=file_key,
        filename=file.filename or file_key,
        recorded_at=recorded_at,
        duration_s=duration_s,
        size_bytes=len(data),
        phone_hint=phone,
        storage_path=path,
        device_id=device_id,
        status="pending",
    )
    # Привязка к ближайшему звонку по номеру и времени.
    if phone:
        since = recorded_at - _MATCH_WINDOW
        until = recorded_at + _MATCH_WINDOW
        calls = (
            await db.execute(
                select(Event)
                .where(Event.type == "call", Event.occurred_at >= since, Event.occurred_at <= until)
                .order_by(Event.occurred_at.desc())
            )
        ).scalars().all()
        for c in calls:
            if phones_match(phone, (c.payload or {}).get("number")):
                rec.call_event_key = c.event_key
                break

    db.add(rec)
    await db.commit()
    return {"status": "accepted", "id": str(rec_id), "matched_call": rec.call_event_key is not None}


@router.get("")
async def list_recordings(
    limit: int = Query(default=50, ge=1, le=500),
    _: str = Depends(require_health_user),
    db: AsyncSession = Depends(get_db),
) -> list[dict]:
    rows = (await db.execute(select(Recording).order_by(Recording.recorded_at.desc()).limit(limit))).scalars().all()
    return [
        {
            "id": str(r.id),
            "filename": r.filename,
            "recorded_at": r.recorded_at.isoformat(),
            "duration_s": r.duration_s,
            "phone_hint": r.phone_hint,
            "status": r.status,
            "language": r.language,
            "transcript": r.transcript,
            "call_event_key": r.call_event_key,
            "error": r.error,
        }
        for r in rows
    ]
