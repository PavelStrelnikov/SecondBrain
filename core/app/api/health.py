import json
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, Request
from fastapi.responses import HTMLResponse
from fastapi.templating import Jinja2Templates
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth import require_health_user
from app.config import settings
from app.db import get_db
from app.models import Event

router = APIRouter(tags=["health"])
templates = Jinja2Templates(directory="app/templates")


@router.get("/api/health")
async def health(db: AsyncSession = Depends(get_db)) -> dict:
    """Без авторизации: только факт, что ядро и база живы, и возраст последнего события."""
    last = (await db.execute(select(func.max(Event.received_at)))).scalar()
    age_s = int((datetime.now(timezone.utc) - last).total_seconds()) if last else None
    return {"status": "ok", "last_event_age_s": age_s}


@router.get("/", response_class=HTMLResponse)
async def health_page(
    request: Request,
    _: str = Depends(require_health_user),
    db: AsyncSession = Depends(get_db),
) -> HTMLResponse:
    tz = ZoneInfo(settings.tz)
    now = datetime.now(tz)
    day_start = now.replace(hour=0, minute=0, second=0, microsecond=0)

    # Последнее событие по каждой паре источник/тип: главный признак, что телефон жив.
    last_by_source = (
        await db.execute(
            select(Event.source, Event.type, func.max(Event.received_at), func.count())
            .group_by(Event.source, Event.type)
            .order_by(Event.source, Event.type)
        )
    ).all()
    sources = []
    for source, type_, last, total in last_by_source:
        age = now - last.astimezone(tz)
        sources.append(
            {
                "source": source,
                "type": type_,
                "last": last.astimezone(tz).strftime("%d.%m %H:%M"),
                "age": _humanize(age),
                "stale": age > timedelta(hours=6),
                "total": total,
            }
        )

    today_counts = (
        await db.execute(
            select(Event.type, func.count()).where(Event.occurred_at >= day_start).group_by(Event.type)
        )
    ).all()

    recent = (
        (await db.execute(select(Event).order_by(Event.occurred_at.desc()).limit(50))).scalars().all()
    )
    rows = [
        {
            "occurred": e.occurred_at.astimezone(tz).strftime("%d.%m %H:%M:%S"),
            "lag_s": int((e.received_at - e.occurred_at).total_seconds()),
            "type": e.type,
            "source": e.source,
            "direction": e.direction or "",
            "summary": _summarize(e),
            "key": e.event_key,
            "received": e.received_at.astimezone(tz).strftime("%d.%m %H:%M:%S"),
            "payload": json.dumps(e.payload or {}, ensure_ascii=False, indent=2),
        }
        for e in recent
    ]

    return templates.TemplateResponse(
        request,
        "health.html",
        {
            "now": now.strftime("%d.%m.%Y %H:%M"),
            "sources": sources,
            "today": dict(today_counts),
            "rows": rows,
        },
    )


def _humanize(delta: timedelta) -> str:
    s = int(delta.total_seconds())
    if s < 60:
        return f"{s} с"
    if s < 3600:
        return f"{s // 60} мин"
    if s < 86400:
        return f"{s // 3600} ч"
    return f"{s // 86400} д"


def _summarize(e: Event) -> str:
    p = e.payload or {}
    if e.type == "call":
        return f"{p.get('number', '?')} · {p.get('duration_s', 0)} с"
    if e.type in ("message", "notification"):
        who = p.get("sender") or p.get("title") or "?"
        text = (p.get("text") or "")[:80]
        return f"{who}: {text}"
    if e.type == "app_state":
        return p.get("state", "")
    return ", ".join(f"{k}={v}" for k, v in list(p.items())[:3])
