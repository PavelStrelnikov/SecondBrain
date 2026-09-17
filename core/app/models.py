import uuid
from datetime import datetime

from sqlalchemy import DateTime, String, func
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class Event(Base):
    """Неизменяемый журнал всего входящего. Источник правды; никогда не редактируется."""

    __tablename__ = "events"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    # Ключ идемпотентности, формирует клиент (телефон): повтор с тем же ключом не создаёт дубля.
    event_key: Mapped[str] = mapped_column(String(200), nullable=False, unique=True)
    # call | message | notification | photo | location | app_state
    type: Mapped[str] = mapped_column(String(40), nullable=False)
    # android | whatsapp | whatsapp_business | telegram | gmail | ...
    source: Mapped[str] = mapped_column(String(40), nullable=False)
    # incoming | outgoing | missed | rejected | None
    direction: Mapped[str | None] = mapped_column(String(10), nullable=True)
    occurred_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    device_id: Mapped[str | None] = mapped_column(String(80), nullable=True)
    payload: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
