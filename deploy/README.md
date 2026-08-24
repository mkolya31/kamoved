# Production-запуск Kamoved

Kamoved разворачивается отдельным Compose-проектом. Его frontend подключается к
общей внешней Docker-сети `web`; центральный Nginx из `/opt/web-gateway`
проксирует запросы на alias `kamoved-frontend`. Backend и PostgreSQL доступны
только во внутренних сетях проекта.

## Подготовка

1. Клонировать репозиторий в `/opt/kamoved`.
2. Скопировать `.env.production.example` в `.env.production`.
3. Заменить демонстрационные пароли на разные длинные случайные значения.
4. Убедиться, что общая сеть создана: `docker network inspect web`.

Файл `.env.production` содержит секреты, исключён из Git и не должен попадать в
репозиторий.

## Email-уведомления

Параметры SMTP Timeweb, пользовательские email, тестовое перенаправление и правила
работы надёжной очереди описаны в
[`docs/03-email-notifications.md`](../docs/03-email-notifications.md).

После изменения почтовых секретов пересоздайте backend тем же способом, что и
после изменения списка пользователей. При `KAMOVED_MAIL_ENABLED=false` отправка
приостанавливается, но уже поставленные в очередь уведомления сохраняются.

## Пользователи

Пользователи перечисляются в `.env.production` последовательными индексами:

```text
KAMOVED_USERS_0_USERNAME=admin
KAMOVED_USERS_0_PASSWORD=длинный-уникальный-пароль
KAMOVED_USERS_0_DISPLAY_NAME=Николай
KAMOVED_USERS_0_EMAIL=nikolay@example.com
KAMOVED_USERS_0_ACTIVE=true

KAMOVED_USERS_1_USERNAME=maksim
KAMOVED_USERS_1_PASSWORD=другой-длинный-уникальный-пароль
KAMOVED_USERS_1_DISPLAY_NAME=Максим
KAMOVED_USERS_1_EMAIL=maksim@example.com
KAMOVED_USERS_1_ACTIVE=true

KAMOVED_USERS_2_USERNAME=third-user
KAMOVED_USERS_2_PASSWORD=третий-длинный-уникальный-пароль
KAMOVED_USERS_2_DISPLAY_NAME=Третий пользователь
KAMOVED_USERS_2_EMAIL=third-user@example.com
KAMOVED_USERS_2_ACTIVE=true
```

После изменения списка нужно пересоздать backend:

```sh
docker compose --env-file .env.production -f compose.production.yaml \
  up -d --no-deps --force-recreate backend
```

При запуске отсутствующие пользователи создаются, а у существующих обновляются
имя, email, пароль и признак активности. Email обязателен и должен быть уникален
без учёта регистра. Пароль повторно хешируется только при его
изменении. Удаление строки из `.env.production` не удаляет и не отключает уже
созданного пользователя; для отключения нужно оставить пользователя в списке и
задать `KAMOVED_USERS_N_ACTIVE=false`.

Старые переменные `KAMOVED_ADMIN_USERNAME`, `KAMOVED_ADMIN_PASSWORD`,
`KAMOVED_ADMIN_DISPLAY_NAME` и `KAMOVED_ADMIN_EMAIL` продолжают поддерживаться, если список
`KAMOVED_USERS_*` не задан.

## Пользовательские сессии

Сессии хранятся в PostgreSQL и сохраняются при пересоздании backend-контейнера.
Бездействующая сессия и абсолютный срок жизни входа ограничены семью сутками.
Просроченные записи удаляются фоновой задачей Spring Session раз в минуту.

Первый релиз с JDBC-сессиями потребует одного повторного входа: текущие
сессии из памяти старого backend нельзя перенести в PostgreSQL. После этого
обычные релизы больше не будут завершать активные сессии.

Принудительно завершить все сессии:

```sh
cd /opt/kamoved
bash deploy/revoke-sessions.sh --all
```

Завершить все сессии одного пользователя:

```sh
cd /opt/kamoved
bash deploy/revoke-sessions.sh maksim
```

Явный выход в интерфейсе сразу удаляет текущую сессию из PostgreSQL.

## Проверка и последовательная сборка

На VPS с 2 ГБ RAM backend и frontend лучше собирать последовательно:

```sh
docker compose --env-file .env.production -f compose.production.yaml config --quiet
docker compose --env-file .env.production -f compose.production.yaml build backend
docker compose --env-file .env.production -f compose.production.yaml build frontend
docker compose --env-file .env.production -f compose.production.yaml up -d --no-build
```

## Подключение домена

Сначала скопировать `deploy/nginx/kamoved.ru.http.conf` в конфигурацию
`/opt/web-gateway/nginx/conf.d/`, проверить и перечитать Nginx. Затем выпустить
сертификат через существующий Certbot:

```sh
cd /opt/web-gateway
docker compose run --rm certbot certonly --webroot \
  --webroot-path /var/www/certbot \
  --email EMAIL --agree-tos --no-eff-email \
  -d kamoved.ru -d www.kamoved.ru
```

После выпуска сертификата заменить bootstrap-файл на полный
`deploy/nginx/kamoved.ru.conf`, проверить и перечитать Nginx:

```sh
cd /opt/web-gateway
docker compose exec nginx nginx -t
docker compose exec nginx nginx -s reload
```

## Диагностика

```sh
docker compose --env-file .env.production -f compose.production.yaml ps
docker compose --env-file .env.production -f compose.production.yaml logs --tail=200
```

## Автоматический деплой

Workflow `.github/workflows/deploy-production.yml` запускается при каждом push в
ветку `production`, в том числе после слияния pull request. GitHub Actions
подключается к VPS отдельным SSH-ключом, который может запустить только
`/opt/kamoved-deploy.sh`.

Серверный скрипт последовательно:

1. получает `production` в `FETCH_HEAD` без обновления remote-tracking refs и синхронизирует `/opt/kamoved` с полученным коммитом;
2. проверяет production Compose-конфигурацию;
3. собирает backend, затем frontend;
4. обновляет контейнеры и проверяет `https://kamoved.ru/`.

В репозитории GitHub должен быть настроен Actions secret `VPS_SSH_KEY` с
приватным ключом, созданным специально для Камоведа. Публичные адрес, порт,
пользователь и закреплённый SSH host key VPS находятся в workflow. Файл
`deploy/kamoved-deploy.sh` хранит эталон серверного скрипта; исполняемая копия
находится вне рабочей директории репозитория, чтобы deploy-ключ не мог изменить
команду, которую ему разрешено запускать.

После изменения эталона исполняемую копию на VPS нужно обновить вручную:

```sh
sudo install -o root -g root -m 0755 \
  /opt/kamoved/deploy/kamoved-deploy.sh /opt/kamoved-deploy.sh
```

## Остановка

```sh
docker compose --env-file .env.production -f compose.production.yaml down
```

Обычная остановка не удаляет данные PostgreSQL. Флаг `-v` использовать нельзя,
если требуется сохранить production-данные.

## Резервные копии PostgreSQL

Production-база каждый час шифруется и сохраняется в приватный Timeweb S3.
Установка, тестовое восстановление и аварийная инструкция описаны в
[`deploy/backup/README.md`](backup/README.md).
