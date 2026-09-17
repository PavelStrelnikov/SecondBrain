from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "postgresql+asyncpg://brain:change_me@localhost:5440/brain_work"
    device_token: str = "change_me"
    health_password: str = "change_me"
    health_user: str = "brain"
    tz: str = "Asia/Jerusalem"
    # Хранилище аудиозаписей внутри контейнера (том)
    audio_dir: str = "/data/recordings"
    # Модель Whisper для расшифровки. small — быстрая для теста; для иврита позже large-v3 или ivrit.ai
    whisper_model: str = "small"
    whisper_device: str = "cpu"
    whisper_compute: str = "int8"
    # Язык подсказкой; пусто = автоопределение (нужно для смеси русского и иврита)
    whisper_language: str = ""


settings = Settings()
