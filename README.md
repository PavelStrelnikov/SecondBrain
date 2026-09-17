# Second Brain

Личный ассистент, который перехватывает звонки, сообщения и письма, превращает их в незакрытые петли и напоминает, пока они не закрыты. Проектный документ: [docs/architecture.md](docs/architecture.md).

Отдельный проект. С ServiceCRM связан только по сети через её MCP-сервер.

## Состав

| Папка | Что это |
| --- | --- |
| `core/` | Ядро: FastAPI + PostgreSQL. Приём событий, петли, обязательства, напоминания |
| `android/` | Приложение-перехватчик для Android: журнал звонков, уведомления WhatsApp, буфер офлайн |
| `docs/` | Архитектура и планы |

## Запуск ядра (неделя 1)

```bash
cp .env.example .env        # заполнить пароли и DEVICE_TOKEN
docker compose up -d --build
open http://localhost:8400  # страница «здоровье», логин brain, пароль из HEALTH_PASSWORD
```

Порты: ядро 8400, Postgres 5440. Не пересекаются с CRM (8300, 5435).

## Приложение

Собирается в GitHub Actions при каждом push в `main`. Готовый APK лежит в разделе Releases под тегом `latest`. На телефоне: скачать, разрешить установку из этого источника, установить. Дальше в приложении: адрес сервера, токен устройства, кнопки разрешений.

## Проверка ядра без телефона

```bash
curl -X POST http://localhost:8400/api/events \
  -H "Authorization: Bearer $DEVICE_TOKEN" -H "Content-Type: application/json" \
  -d '{"events":[{"event_key":"test-1","type":"call","source":"android","occurred_at":"2026-09-17T10:00:00+03:00","payload":{"direction":"incoming","number":"+972501234567","duration_s":42}}]}'
```
