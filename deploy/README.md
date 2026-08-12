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

1. синхронизирует `/opt/kamoved` с `origin/production`;
2. проверяет production Compose-конфигурацию;
3. собирает backend, затем frontend;
4. обновляет контейнеры и проверяет `https://kamoved.ru/`.

В репозитории GitHub должен быть настроен Actions secret `VPS_SSH_KEY` с
приватным ключом, созданным специально для Камоведа. Публичные адрес, порт,
пользователь и закреплённый SSH host key VPS находятся в workflow. Файл
`deploy/kamoved-deploy.sh` хранит эталон серверного скрипта; исполняемая копия
находится вне рабочей директории репозитория, чтобы deploy-ключ не мог изменить
команду, которую ему разрешено запускать.

## Остановка

```sh
docker compose --env-file .env.production -f compose.production.yaml down
```

Обычная остановка не удаляет данные PostgreSQL. Флаг `-v` использовать нельзя,
если требуется сохранить production-данные.
