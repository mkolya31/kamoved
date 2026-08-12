# Камовед

«Камовед» — электронный журнал продаж и заказов для магазина строительных материалов.

Первый вертикальный срез позволяет продавцу войти в приложение, оформить продажу из
наличия и сразу увидеть её первой в журнале.

## Структура

- `backend` — Java 21, Spring Boot, PostgreSQL, Flyway;
- `frontend` — React, TypeScript, Vite;
- `compose.yaml` — PostgreSQL, backend и nginx с собранным frontend.

## Локальный запуск без Docker

1. Запустить PostgreSQL и создать базу `kamoved`.
2. Задать при необходимости переменные `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
   `KAMOVED_ADMIN_USERNAME` и `KAMOVED_ADMIN_PASSWORD`.
3. Установить JDK 21+ и запустить `mvnw.cmd spring-boot:run` из каталога `backend`.
4. Выполнить `pnpm install` и `pnpm dev` в каталоге `frontend`.
5. Открыть `http://localhost:5173` и войти как `admin` / `change-me-local`.

Пароль по умолчанию предназначен только для локальной разработки.

## Запуск через Docker Compose

Скопировать `.env.example` в `.env`, изменить пароль администратора и выполнить:

```text
docker compose up --build
```

Приложение будет доступно на `http://localhost:8088`.
