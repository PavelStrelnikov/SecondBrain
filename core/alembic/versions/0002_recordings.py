"""recordings table

Revision ID: 0002
Revises: 0001
Create Date: 2026-09-17

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision: str = "0002"
down_revision: Union[str, None] = "0001"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "recordings",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("file_key", sa.String(300), nullable=False, unique=True),
        sa.Column("filename", sa.String(300), nullable=False),
        sa.Column("recorded_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("received_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("duration_s", sa.Integer(), nullable=True),
        sa.Column("size_bytes", sa.Integer(), nullable=True),
        sa.Column("phone_hint", sa.String(50), nullable=True),
        sa.Column("storage_path", sa.String(500), nullable=False),
        sa.Column("device_id", sa.String(80), nullable=True),
        # pending | transcribing | done | error
        sa.Column("status", sa.String(20), nullable=False, server_default="pending"),
        sa.Column("transcript", sa.Text(), nullable=True),
        sa.Column("language", sa.String(10), nullable=True),
        sa.Column("error", sa.String(500), nullable=True),
        sa.Column("call_event_key", sa.String(200), nullable=True),
    )
    op.create_index("ix_recordings_status", "recordings", ["status"])
    op.create_index("ix_recordings_recorded_at", "recordings", ["recorded_at"])


def downgrade() -> None:
    op.drop_index("ix_recordings_recorded_at", table_name="recordings")
    op.drop_index("ix_recordings_status", table_name="recordings")
    op.drop_table("recordings")
