"""events table

Revision ID: 0001
Revises:
Create Date: 2026-09-17

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision: str = "0001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "events",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("event_key", sa.String(200), nullable=False, unique=True),
        sa.Column("type", sa.String(40), nullable=False),
        sa.Column("source", sa.String(40), nullable=False),
        sa.Column("direction", sa.String(10), nullable=True),
        sa.Column("occurred_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("received_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("device_id", sa.String(80), nullable=True),
        sa.Column("payload", postgresql.JSONB(), nullable=False, server_default="{}"),
    )
    op.create_index("ix_events_occurred_at", "events", ["occurred_at"])
    op.create_index("ix_events_type_occurred", "events", ["type", "occurred_at"])
    op.create_index("ix_events_received_at", "events", ["received_at"])


def downgrade() -> None:
    op.drop_index("ix_events_received_at", table_name="events")
    op.drop_index("ix_events_type_occurred", table_name="events")
    op.drop_index("ix_events_occurred_at", table_name="events")
    op.drop_table("events")
