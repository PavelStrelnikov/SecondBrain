import logging

from fastapi import FastAPI

from app.api import events, health, recordings

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")

app = FastAPI(title="Second Brain core", version="0.1.0")
app.include_router(health.router)
app.include_router(events.router)
app.include_router(recordings.router)
