# Проверка кандидата перед merge в production

Pull request в `production` можно слить только после проверки предполагаемого
merge-коммита на production VPS. Проверка запускает тесты, собирает production-образы
и поднимает их с настоящим `.env.production`, но с временной базой данных и без
доступа контейнеров во внешнюю сеть.

## Что проверяется

- исходная ветка — `develop` или ветка этого же репозитория с префиксом `hotfix/`;
- backend-тесты из Dockerfile target `test`;
- frontend-тесты Vitest из Dockerfile target `test`;
- production Compose-конфигурация кандидата;
- production-сборка backend и frontend;
- запуск backend на чистой PostgreSQL и применение всех миграций;
- ответы frontend и `GET /api/auth/csrf` через frontend Nginx.

Playwright E2E в эту проверку не входят. Временное окружение использует отдельные
Compose project, сеть, контейнеры, образы и volume. Production-контейнеры и
`postgres-data` проекта `kamoved` не используются.

## Первичная настройка VPS

После merge этой задачи в `develop`, но до первого защищённого merge в
`production`, выполнить следующие шаги на VPS.

### Шаг 1. Получить файлы из develop

```sh
git -C /opt/kamoved fetch --no-tags origin \
  refs/heads/develop:refs/remotes/origin/develop
```

Команда обращается к `origin`, получает актуальный `develop` и обновляет локальную
ссылку `origin/develop`. Флаг `--no-tags` не загружает ненужные для настройки теги.
Параметр `-C /opt/kamoved` выполняет Git-команду в каталоге приложения без
предварительного `cd`.

Рабочее дерево `/opt/kamoved` остаётся на текущем production-коммите: команда не
выполняет `checkout`, `switch`, `reset` или merge.

### Шаг 2. Создать каталог служебных файлов Kamoved

```sh
sudo install -d -o root -g root -m 0755 /opt/kamoved-deploy
```

`install -d` создаёт общий каталог `/opt/kamoved-deploy` для root-owned скриптов и
конфигурации Kamoved. Владелец `root:root` и права `0755` разрешают пользователю
`laptop` читать и запускать файлы, но не добавлять и не заменять их.

Команда безопасна при повторном выполнении: существующий каталог не удаляется.

### Шаг 3. Создать каталог для временных worktree

```sh
sudo install -d -o laptop -g laptop -m 0750 \
  /opt/kamoved-deploy/worktrees
```

Этот подкаталог принадлежит пользователю, от имени которого запускается
SSH-проверка. Только здесь preflight-скрипту разрешено создавать и удалять
временные Git worktree кандидатов.

- `-o laptop -g laptop` назначает владельцем и группой пользователя `laptop`;
- `-m 0750` разрешает владельцу читать, изменять и открывать каталог, группе —
  читать и открывать, остальным запрещает доступ.

Корень `/opt` при этом не заполняется отдельными временными каталогами каждого
сервиса.

### Шаг 4. Установить production deploy-скрипт

```sh
sudo install -o root -g root -m 0755 \
  /opt/kamoved/deploy/kamoved-deploy.sh \
  /opt/kamoved-deploy/deploy.sh
```

Команда копирует уже используемый production deploy-скрипт в общий каталог и
сразу назначает владельца `root:root` и права `0755`.

Старый файл пока не удаляется, чтобы перенос можно было проверить без риска
потерять рабочий деплой.

### Шаг 5. Обновить forced command production deploy-ключа

В `/home/laptop/.ssh/authorized_keys` найти строку существующего deploy-ключа и
заменить в ней только путь forced command:

```text
command="/opt/kamoved-deploy.sh"
```

на:

```text
command="/opt/kamoved-deploy/deploy.sh"
```

Публичную часть ключа и остальные ограничения менять не нужно. Следующий запуск
workflow `Deploy Kamoved production` должен использовать скрипт из нового
каталога.

### Шаг 6. Установить серверный preflight-скрипт

```sh
git -C /opt/kamoved show \
  origin/develop:deploy/kamoved-preflight.sh \
  | sudo tee /opt/kamoved-deploy/preflight.sh >/dev/null
```

`git show` читает `deploy/kamoved-preflight.sh` непосредственно из полученного
`origin/develop`, не переключая production checkout. `sudo tee` записывает его в
`/opt/kamoved-deploy/preflight.sh`, куда обычный пользователь писать не должен.
Перенаправление `>/dev/null` скрывает содержимое файла из терминала.

Этот путь находится вне Git working tree, поэтому будущий PR не сможет изменить
исполняемый forced command простым изменением файла в репозитории.

### Шаг 7. Установить изолированную Compose-конфигурацию

```sh
git -C /opt/kamoved show \
  origin/develop:deploy/compose.preflight.yaml \
  | sudo tee /opt/kamoved-deploy/compose.preflight.yaml >/dev/null
```

Команда тем же способом устанавливает root-owned Compose-файл. Он задаёт
временную PostgreSQL, внутреннюю сеть без внешнего доступа и контейнеры кандидата.
Скрипт использует именно эту установленную копию, а не версию Compose-файла из PR.

### Шаг 8. Запретить изменение установленных файлов CI-пользователем

```sh
sudo chown root:root \
  /opt/kamoved-deploy/preflight.sh \
  /opt/kamoved-deploy/compose.preflight.yaml
```

Оба файла становятся собственностью `root`. Пользователь `laptop`, под которым
работает SSH-проверка, сможет читать и запускать их, но не сможет переписать.

### Шаг 9. Назначить минимально необходимые права

```sh
sudo chmod 0755 /opt/kamoved-deploy/preflight.sh
sudo chmod 0644 /opt/kamoved-deploy/compose.preflight.yaml
```

Первая команда делает shell-скрипт исполняемым, сохраняя право записи только у
`root`. Вторая оставляет Compose-файл доступным только для чтения всем, кроме
`root`, которому разрешена запись.

### Шаг 10. Удалить старую копию deploy-скрипта после проверки

Не выполнять этот шаг, пока хотя бы один production-деплой не завершился успешно
с новым forced command. После успешной проверки удалить прежнюю копию:

```sh
sudo rm -- /opt/kamoved-deploy.sh
```

Команда удаляет только старую исполняемую копию из корня `/opt`. Эталон остаётся в
`/opt/kamoved/deploy/kamoved-deploy.sh`, а активная root-owned копия — в
`/opt/kamoved-deploy/deploy.sh`.

На этом перенос и первичная установка серверных файлов завершены.

## Обновление серверных файлов

После попадания файлов в `production` установленные копии можно обновлять из
рабочего дерева `/opt/kamoved`.

### Шаг 1. Обновить preflight-скрипт

```sh
sudo install -o root -g root -m 0755 \
  /opt/kamoved/deploy/kamoved-preflight.sh \
  /opt/kamoved-deploy/preflight.sh
```

Команда копирует актуальный скрипт, сразу назначает владельца `root:root` и права
`0755`.

### Шаг 2. Обновить Compose-конфигурацию

```sh
sudo install -o root -g root -m 0644 \
  /opt/kamoved/deploy/compose.preflight.yaml \
  /opt/kamoved-deploy/compose.preflight.yaml
```

Команда копирует актуальный Compose-файл с владельцем `root:root` и правами
`0644`. После любого последующего изменения файлов в `deploy/` оба шага нужно
выполнить вручную.

## Отдельный SSH-ключ проверки

### Шаг 1. Создать пару ключей

На доверенном компьютере выполнить:

```sh
ssh-keygen -t ed25519 -f kamoved-production-preflight \
  -C kamoved-production-preflight
```

`-t ed25519` выбирает алгоритм ключа, `-f` задаёт имена приватного и публичного
файлов, а `-C` добавляет понятный комментарий. Когда `ssh-keygen` запросит
passphrase, нажать Enter дважды и оставить её пустой: workflow запускается
неинтерактивно и не сможет ввести пароль ключа. Безопасность этого ключа
обеспечивают GitHub secret и ограничения forced command. Будут созданы:

- `kamoved-production-preflight` — приватный ключ;
- `kamoved-production-preflight.pub` — публичный ключ.

### Шаг 2. Добавить приватный ключ в GitHub

Содержимое `kamoved-production-preflight` сохранить в GitHub Actions secret
`VPS_CI_SSH_KEY` без дополнительных пробелов или кавычек. Приватный ключ нельзя
добавлять на VPS в `authorized_keys` или хранить в репозитории.

### Шаг 3. Ограничить публичный ключ на VPS

Содержимое `kamoved-production-preflight.pub` добавить в
`/home/laptop/.ssh/authorized_keys` одной строкой, заменив `PUBLIC_KEY` реальной
частью публичного ключа:

```text
restrict,command="/opt/kamoved-deploy/preflight.sh" ssh-ed25519 PUBLIC_KEY kamoved-production-preflight
```

`restrict` запрещает PTY, forwarding и другие возможности SSH. Forced command
передаёт серверному скрипту только запрошенную команду через
`SSH_ORIGINAL_COMMAND`; скрипт принимает исключительно номер PR и два SHA в
строго заданном формате.

Существующий `VPS_SSH_KEY` продолжает использоваться только для production-деплоя.

## GitHub ruleset для production

Сначала нужно добавить `VPS_CI_SSH_KEY`, установить серверные файлы и открыть PR
`develop → production`. Workflow уже находится в default-ветке `develop`, поэтому
проверит и первый production PR. После первого результата workflow
`Check Kamoved production candidate`, до merge этого PR, в
`Settings → Rules → Rulesets` создать активный branch ruleset:

1. Target branches: `production`.
2. Bypass list: пустой, включая администраторов.
3. Включить `Restrict deletions` и `Block force pushes`.
4. Включить `Require a pull request before merging`; оставить `Required
   approvals` равным `0`, если отдельное ревью не требуется.
5. Включить `Require status checks to pass`.
6. Добавить обязательный status check `Production readiness`, источник — GitHub
   Actions.
7. Включить требование актуальности ветки перед merge (`Require branches to be up
   to date before merging`).

Прямые push после включения ruleset запрещены. Ограничение исходной ветки
проверяется доверенным workflow: PR из любой ветки, кроме `develop` и `hotfix/*`,
получит неуспешный статус `Production readiness`.

Workflow использует `pull_request_target`, не делает checkout и не выполняет код
PR на GitHub runner. Это не позволяет PR изменить шаг, которому передаётся секрет.
Кандидат выполняется только на VPS после проверки, что PR создан из разрешённой
ветки этого же репозитория. Отдельный SSH-ключ может запустить только root-owned
preflight-скрипт.

## Последовательность и очистка

Повторные запуски одного PR упорядочиваются его собственной GitHub Actions
concurrency group. На VPS preflight и production-деплой используют общий lock
`/tmp/kamoved-deploy.lock`; проверка ждёт освобождения lock не более 20 минут.
Поэтому проверки разных PR и деплой не выполняют тяжёлые Docker-операции
параллельно.

При любом завершении preflight-скрипт останавливает временные контейнеры, удаляет
их volume и локальные образы, удаляет Git worktree и временный каталог. Production
Compose project при этом не затрагивается.

## Диагностика

Результат отображается в PR как status check `Production readiness`. Подробный
вывод тестов, сборки и smoke-проверки находится в соответствующем GitHub Actions
run. Скрипты не выводят содержимое `.env.production`.

Проверить установленные файлы на VPS:

```sh
sudo cmp /opt/kamoved/deploy/kamoved-preflight.sh \
  /opt/kamoved-deploy/preflight.sh
sudo cmp /opt/kamoved/deploy/compose.preflight.yaml \
  /opt/kamoved-deploy/compose.preflight.yaml
```
