from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "postgresql+asyncpg://brain:change_me@localhost:5440/brain_work"
    device_token: str = "change_me"
    health_password: str = "change_me"
    health_user: str = "brain"
    tz: str = "Asia/Jerusalem"


settings = Settings()
