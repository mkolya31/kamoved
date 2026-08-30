# Откат приложения по тегу

Инструкция предназначена для быстрого ручного восстановления production на VPS.
Она откатывает backend и frontend, но не изменяет данные PostgreSQL.

## Выполнение отката

Подключиться к VPS обычным SSH-пользователем с доступом к Docker, затем выполнить:

```sh
cd /opt/kamoved

ROLLBACK_TAG=v0.11.0
git fetch --tags origin
git rev-parse --verify "$ROLLBACK_TAG^{commit}"
git switch --detach "$ROLLBACK_TAG"

docker compose --env-file .env.production -f compose.production.yaml config --quiet
docker compose --env-file .env.production -f compose.production.yaml build backend
docker compose --env-file .env.production -f compose.production.yaml build frontend
docker compose --env-file .env.production -f compose.production.yaml \
  up -d --no-build --remove-orphans
```

Заменить `v0.11.0` на нужный существующий тег. Backend и frontend необходимо
откатывать вместе, чтобы их API оставались совместимыми.

## Проверка

```sh
docker compose --env-file .env.production -f compose.production.yaml ps
curl --fail https://kamoved.ru/
curl --fail https://kamoved.ru/api/auth/csrf
```

При ошибке проверить логи:

```sh
docker compose --env-file .env.production -f compose.production.yaml \
  logs --tail=200 backend frontend
```

## Ограничения

- Не использовать `docker compose down -v`: команда удалит production-данные.
- Схема базы не откатывается. Перед переходом на старую версию нужно убедиться,
  что она совместима с уже применёнными миграциями.
- Репозиторий останется в состоянии detached HEAD. Это нормально для временного
  ручного отката.
- `/opt/kamoved-deploy.sh` и GitHub Actions снова развернут текущую ветку
  `production`. После восстановления сервиса нужно отдельно исправить или
  откатить эту ветку до безопасной версии.
