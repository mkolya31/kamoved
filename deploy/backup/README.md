# Резервные копии PostgreSQL

Production-база Kamoved каждый час сохраняется в приватный бакет Timeweb S3. `pg_dump` создаёт
согласованный архив без остановки приложения, `pg_restore --list` проверяет структуру архива, а
`restic` шифрует его до отправки в S3. Незашифрованный временный файл доступен только `root` и
удаляется сразу после успешной загрузки.

Политика хранения по умолчанию:

- 48 почасовых снимков;
- 30 ежедневных снимков;
- 13 еженедельных снимков.

Раз в месяц последний снимок полностью восстанавливается во временный PostgreSQL-контейнер с
отключённой сетью. Production-база при этой проверке не изменяется.

## Первичная установка на VPS

Бакет должен быть **приватным**. Для репозитория `restic` не следует включать Object Lock: ротация
должна иметь возможность удалять устаревшие внутренние объекты.

Установить `restic` из пакетов ОС, затем запустить установщик:

```sh
sudo apt-get update
sudo apt-get install -y restic
cd /opt/kamoved
sudo bash deploy/backup/install-systemd.sh
```

Заполнить `/etc/kamoved/backup.env`. Данные находятся в дашборде бакета Timeweb:

```dotenv
AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...
AWS_DEFAULT_REGION=ru-1
RESTIC_REPOSITORY=s3:https://s3.twcstorage.ru/ИМЯ-БАКЕТА/kamoved-restic
RESTIC_PASSWORD_FILE=/etc/kamoved/restic-password
```

Ограничить права и обязательно сохранить пароль шифрования вне VPS, например в менеджере паролей:

```sh
sudo chown root:root /etc/kamoved/backup.env /etc/kamoved/restic-password
sudo chmod 600 /etc/kamoved/backup.env /etc/kamoved/restic-password
```

Если потерять `/etc/kamoved/restic-password` вместе с VPS, расшифровать копии будет невозможно.
Access Key и Secret Key сами по себе пароль шифрования не заменяют.

## Первый бэкап и включение расписания

Инициализация выполняется один раз:

```sh
sudo bash /opt/kamoved/deploy/backup/kamoved-backup.sh init
sudo systemctl start kamoved-backup.service
sudo systemctl status kamoved-backup.service
sudo bash /opt/kamoved/deploy/backup/kamoved-backup.sh snapshots
```

После первого успешного снимка выполнить настоящее тестовое восстановление:

```sh
sudo systemctl start kamoved-backup-verify.service
sudo systemctl status kamoved-backup-verify.service
```

Только после обеих успешных проверок включить таймеры:

```sh
sudo systemctl enable --now kamoved-backup.timer kamoved-backup-verify.timer
sudo systemctl list-timers 'kamoved-backup*'
```

Логи и ручная диагностика:

```sh
sudo journalctl -u kamoved-backup.service -n 100 --no-pager
sudo journalctl -u kamoved-backup-verify.service -n 100 --no-pager
sudo bash /opt/kamoved/deploy/backup/kamoved-backup.sh check
```

Ошибки выполнения сохраняются в `systemd journal`. Внешний алертинг на этом этапе не настроен,
поэтому журналы и состояние таймеров необходимо контролировать отдельно.

## Аварийное восстановление

Сначала посмотреть снимки и выбрать нужный ID:

```sh
sudo bash /opt/kamoved/deploy/backup/kamoved-backup.sh snapshots
```

Загрузить выбранный снимок в защищённый локальный файл (вместо `latest` можно указать ID):

```sh
sudo -i
set -a
source /etc/kamoved/backup.env
set +a
umask 077
restic dump \
  --host kamoved-production \
  --path /var/lib/kamoved-backup/kamoved-postgresql.dump \
  latest /var/lib/kamoved-backup/kamoved-postgresql.dump \
  > /root/kamoved-restore.dump
```

Перед изменением production остановить backend и, если текущая база читается, сделать дополнительный
страховочный дамп. Затем восстановить выбранную копию одной транзакцией:

```sh
cd /opt/kamoved
docker compose --env-file .env.production -f compose.production.yaml stop backend
docker compose --env-file .env.production -f compose.production.yaml exec -T database sh -ec \
  'exec pg_dump --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --format=custom' \
  > /root/kamoved-before-restore.dump

docker compose --env-file .env.production -f compose.production.yaml exec -T database sh -ec \
  'exec pg_restore --clean --if-exists --exit-on-error --single-transaction --no-owner --no-privileges --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
  < /root/kamoved-restore.dump

docker compose --env-file .env.production -f compose.production.yaml start backend
```

После проверки приложения удалить незашифрованные файлы `/root/kamoved-restore.dump` и
`/root/kamoved-before-restore.dump`.
