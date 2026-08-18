# 03. Резервные копии и восстановление «Камоведа»

**Назначение:** быстро проверить резервные копии или восстановить production-базу PostgreSQL после
сбоя. Все команды выполняются на VPS от пользователя с доступом к `sudo`.

## Что необходимо для восстановления

До аварии должны быть доступны вне VPS:

- пароль шифрования `restic` из `/etc/kamoved/restic-password`, сохранённый в Google Менеджере
  Паролей;
- `AWS_ACCESS_KEY_ID` и `AWS_SECRET_ACCESS_KEY` приватного бакета Timeweb S3;
- имя бакета и адрес репозитория `RESTIC_REPOSITORY`;
- доступ к Git-репозиторию и production-секретам «Камоведа».

Никому не отправлять эти значения в сообщениях и не добавлять их в Git. Пароль `restic` и ключи S3
решают разные задачи: ключи открывают доступ к бакету, а пароль расшифровывает его содержимое.

## Быстрая диагностика

Проверить расписание, последний результат и список снимков:

```sh
sudo systemctl list-timers 'kamoved-backup*'
sudo systemctl status kamoved-backup.service --no-pager
sudo journalctl -u kamoved-backup.service -n 100 --no-pager
sudo bash /opt/kamoved/deploy/backup/kamoved-backup.sh snapshots
```

Успешный снимок должен присутствовать в выводе `snapshots`. Ошибка последнего запуска не означает,
что старые снимки повреждены: их нужно проверить отдельно следующими командами.

## Восстановление production-базы на существующем VPS

Эта процедура заменяет содержимое рабочей базы выбранным снимком. Во время восстановления
приложение не принимает новые записи.

### 1. Выбрать снимок

```sh
sudo bash /opt/kamoved/deploy/backup/kamoved-backup.sh snapshots
```

Выбрать ID нужного снимка из первого столбца. Обычно используется последний снимок перед сбоем. Не
использовать `latest`, если есть сомнение, что последний снимок содержит корректные данные. На
следующем шаге ID будет помещён во временную переменную терминала — записывать его в файл не нужно.

### 2. Скачать и расшифровать выбранный снимок

После входа в root-сессию заменить `ВСТАВИТЬ_ID_СНИМКА` на выбранный ID. Переменная существует только
в этой сессии терминала:

```sh
sudo -i
SNAPSHOT_ID='ВСТАВИТЬ_ID_СНИМКА'
set -a
source /etc/kamoved/backup.env
set +a
umask 077

restic dump \
  --host kamoved-production \
  --path /var/lib/kamoved-backup/kamoved-postgresql.dump \
  "$SNAPSHOT_ID" /var/lib/kamoved-backup/kamoved-postgresql.dump \
  > /root/kamoved-restore.dump

test -s /root/kamoved-restore.dump
```

Если `restic dump` или `test` завершились ошибкой, остановиться: рабочая база ещё не изменена.

### 3. Проверить скачанный архив

```sh
cd /opt/kamoved
docker compose --env-file .env.production -f compose.production.yaml exec -T database \
  pg_restore --list < /root/kamoved-restore.dump > /dev/null
```

Продолжать только при коде завершения `0`.

### 4. Остановить backend и сохранить текущее состояние базы

```sh
docker compose --env-file .env.production -f compose.production.yaml stop backend

docker compose --env-file .env.production -f compose.production.yaml exec -T database sh -ec \
  'exec pg_dump --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --format=custom --no-owner --no-privileges' \
  > /root/kamoved-before-restore.dump

test -s /root/kamoved-before-restore.dump
```

Если текущая база повреждена настолько, что страховочный дамп создать невозможно, не запускать
backend и перейти к следующему шагу только после осознанного решения восстанавливать выбранный
снимок.

### 5. Заменить рабочую базу выбранным снимком

```sh
docker compose --env-file .env.production -f compose.production.yaml exec -T database sh -ec \
  'exec pg_restore --clean --if-exists --exit-on-error --single-transaction --no-owner --no-privileges --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
  < /root/kamoved-restore.dump
```

Параметр `--single-transaction` не допускает частичного восстановления: при ошибке изменения
откатываются одной транзакцией.

### 6. Запустить приложение и проверить данные

```sh
docker compose --env-file .env.production -f compose.production.yaml start backend
docker compose --env-file .env.production -f compose.production.yaml ps
docker compose --env-file .env.production -f compose.production.yaml logs --tail=100 backend
```

Открыть `https://kamoved.ru/`, войти и проверить последние записи журнала. После успешной проверки
сразу создать новый внешний снимок:

```sh
sudo systemctl start kamoved-backup.service
sudo systemctl status kamoved-backup.service --no-pager
```

### 7. Удалить временные незашифрованные дампы

Только после проверки приложения и нового бэкапа:

```sh
sudo rm -f -- /root/kamoved-restore.dump /root/kamoved-before-restore.dump
exit
```

## Восстановление после полной потери VPS

1. Подготовить новый Linux-сервер, установить Docker Compose и `restic`.
2. Развернуть production-версию «Камоведа» в `/opt/kamoved` и создать `.env.production`.
3. Выполнить `sudo bash /opt/kamoved/deploy/backup/install-systemd.sh`.
4. Заполнить `/etc/kamoved/backup.env` прежними ключами S3 и тем же адресом
   `RESTIC_REPOSITORY`. Команду `restic init` повторно **не запускать**.
5. Поместить сохранённый пароль шифрования в `/etc/kamoved/restic-password` и установить права:

   ```sh
   sudo chown root:root /etc/kamoved/backup.env /etc/kamoved/restic-password
   sudo chmod 600 /etc/kamoved/backup.env /etc/kamoved/restic-password
   ```

6. Запустить PostgreSQL без backend, чтобы новые записи не появились до восстановления:

   ```sh
   cd /opt/kamoved
   docker compose --env-file .env.production -f compose.production.yaml up -d database
   ```

7. Убедиться, что старые снимки читаются:

   ```sh
   sudo bash /opt/kamoved/deploy/backup/kamoved-backup.sh snapshots
   sudo bash /opt/kamoved/deploy/backup/kamoved-backup.sh check
   ```

8. Выполнить шаги 1–3 и 5–7 из раздела «Восстановление production-базы на существующем VPS».
   Страховочный дамп новой пустой базы на этом этапе не нужен.
9. Включить таймеры только после успешного запуска и ручной проверки данных:

   ```sh
   sudo systemctl enable --now kamoved-backup.timer kamoved-backup-verify.timer
   sudo systemctl list-timers 'kamoved-backup*'
   ```

## Ручная проверка бэкапа без изменения production

### 1. Проверить структуру репозитория

```sh
sudo bash /opt/kamoved/deploy/backup/kamoved-backup.sh check
```

Ожидаемый результат: `no errors were found`. Эта команда проверяет метаданные репозитория, но не
выполняет настоящее восстановление базы.

### 2. Полностью восстановить последний снимок в изолированный PostgreSQL

```sh
sudo systemctl start kamoved-backup-verify.service
sudo systemctl status kamoved-backup-verify.service --no-pager
sudo journalctl -u kamoved-backup-verify.service -n 150 --no-pager
```

Проверка скачивает все данные из S3, поднимает временный PostgreSQL-контейнер без сети,
восстанавливает в него последний снимок и проверяет наличие таблиц. Production-база не изменяется.
Успешный журнал заканчивается сообщением вида:

```text
Restore verification completed successfully (4 public tables)
```

Если проверка завершилась ошибкой, не удалять имеющиеся снимки и не выполнять `restic forget` или
`restic prune` вручную до выяснения причины.

## Как устроен механизм бэкапа

Раз в час `systemd` запускает `kamoved-backup.service`. Скрипт создаёт согласованный `pg_dump`
работающей PostgreSQL, проверяет архив через `pg_restore --list`, затем `restic` шифрует его и
загружает в приватный Timeweb S3. Незашифрованный временный дамп удаляется после загрузки или при
ошибке. Хранятся 48 почасовых, 30 ежедневных и 13 еженедельных снимков.

Раз в месяц `kamoved-backup-verify.service` проверяет все объекты репозитория и действительно
восстанавливает последний снимок во временный изолированный PostgreSQL. Рабочая база при этом не
затрагивается. Ошибки сохраняются в `systemd journal`; внешний автоматический алертинг пока не
настроен.
