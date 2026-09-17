import secrets

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBasic, HTTPBasicCredentials, HTTPBearer

from app.config import settings

bearer = HTTPBearer(auto_error=False)
basic = HTTPBasic(auto_error=False)


def require_device(creds: HTTPAuthorizationCredentials | None = Depends(bearer)) -> str:
    """Телефон подписывает запросы токеном устройства. Пока один токен из .env; таблица устройств позже."""
    if creds is None or not secrets.compare_digest(creds.credentials, settings.device_token):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="bad device token")
    return "device-1"


def require_health_user(creds: HTTPBasicCredentials | None = Depends(basic)) -> str:
    if (
        creds is None
        or not secrets.compare_digest(creds.username, settings.health_user)
        or not secrets.compare_digest(creds.password, settings.health_password)
    ):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="auth required",
            headers={"WWW-Authenticate": "Basic"},
        )
    return creds.username
