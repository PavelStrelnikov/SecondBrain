"""Фоновый воркер расшифровки записей звонков.

Отдельный процесс: раз в несколько секунд берёт запись со статусом pending,
прогоняет через локальный Whisper (faster-whisper на процессоре) и сохраняет транскрипт.
Модель на процессоре не блокирует ядро, потому что воркер — отдельный контейнер.
"""
import asyncio
import logging

from sqlalchemy import select, update

from app.config import settings
from app.db import async_session
from app.models import Recording

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s worker: %(message)s")
log = logging.getLogger("worker")

_model = None


def _load_model():
    global _model
    if _model is None:
        from faster_whisper import WhisperModel

        log.info("loading whisper model %s (%s/%s)", settings.whisper_model, settings.whisper_device, settings.whisper_compute)
        _model = WhisperModel(settings.whisper_model, device=settings.whisper_device, compute_type=settings.whisper_compute)
    return _model


def _transcribe(path: str) -> tuple[str, str]:
    """Синхронная расшифровка одного файла. Возвращает (текст, язык)."""
    model = _load_model()
    lang = settings.whisper_language or None
    segments, info = model.transcribe(path, language=lang, vad_filter=True, beam_size=1)
    text = " ".join(seg.text.strip() for seg in segments).strip()
    return text, info.language


async def _claim_one() -> Recording | None:
    """Берём одну запись pending и помечаем transcribing, чтобы не взять дважды."""
    async with async_session() as db:
        rec = (
            await db.execute(select(Recording).where(Recording.status == "pending").order_by(Recording.recorded_at).limit(1))
        ).scalar_one_or_none()
        if rec is None:
            return None
        await db.execute(update(Recording).where(Recording.id == rec.id).values(status="transcribing"))
        await db.commit()
        return rec


async def _finish(rec_id, *, transcript=None, language=None, error=None):
    async with async_session() as db:
        values = {"status": "done" if error is None else "error"}
        if transcript is not None:
            values["transcript"] = transcript
        if language is not None:
            values["language"] = language
        if error is not None:
            values["error"] = error[:500]
        await db.execute(update(Recording).where(Recording.id == rec_id).values(**values))
        await db.commit()


async def main() -> None:
    log.info("transcribe worker started, polling every 5s")
    while True:
        try:
            rec = await _claim_one()
        except Exception:  # noqa: BLE001
            # База ещё мигрирует или временно недоступна: подождём и попробуем снова, не падая.
            log.warning("db not ready yet, retrying in 5s")
            await asyncio.sleep(5)
            continue
        if rec is None:
            await asyncio.sleep(5)
            continue
        log.info("transcribing %s", rec.filename)
        try:
            text, language = await asyncio.to_thread(_transcribe, rec.storage_path)
            await _finish(rec.id, transcript=text, language=language)
            log.info("done %s (%s, %d chars)", rec.filename, language, len(text))
        except Exception as e:  # noqa: BLE001
            log.exception("transcription failed for %s", rec.filename)
            try:
                await _finish(rec.id, error=f"{type(e).__name__}: {e}")
            except Exception:  # noqa: BLE001
                log.exception("failed to record error status")


if __name__ == "__main__":
    asyncio.run(main())
