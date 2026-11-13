# Закрытый demo: воспроизводимый deployment package

Пакет предназначен для отдельного Linux VPS (ориентир Ubuntu 24.04, Java 21, systemd,
Nginx, Certbot, Python 3.10+). Подготовка и тесты не обращаются к серверу. Команды установки
ниже выполняет владелец только в отдельно разрешённой серверной задаче, с обычным sudo.
Пароли вводятся интерактивно; NOPASSWD не требуется. Не запускать пакет на сервере с чужими
сайтами: аварийный rollback останавливает и mask весь указанный Nginx service.

**Каждый restart, reboot, аварийное завершение, update и rollback Java стирает demo-данные.**
Используется только `jdbc:h2:mem:pmdemo_vps`, профиль demo, H2 Console выключена, create-drop.
Рабочую файловую БД, её копии и каталоги data/ не переносить. IsolatedDatabaseGuard не отключён.
Это закрытый пилот, не постоянное production-хранилище.

## Входные параметры и ресурсы

Нужны: согласованные VPS/ОС/архитектура, домен и DNS, административный SSH-доступ с sudo,
реальная ACME-почта (в Certbot), ответственный за продление и участники Basic Auth.
A/AAAA должны вести на сервер; AAAA включать только с проверенной IPv6-связностью.
Практический ориентир малого пилота: 1 vCPU, 2 GiB RAM, 10 GiB диска. Это оценка,
не нагрузочная гарантия. Heap 128..512 MiB, MemoryMax=1G; H2 и списки растут в RAM.

## Сборка JAR и генерация пакета

Сборочная машина: JDK 21, Maven 3.9.9. На VPS достаточно JRE. Собирать из полного принятого
SHA, не из произвольного HEAD. Builder использует git archive pom.xml/src и закреплённый
outputTimestamp; checkout/reset/merge/stash не выполняет. По умолчанию сохранён SHA ранее
принятых исходников, новый релиз задаётся явно:

```sh
python3 deploy/demo/package.py build --repo . --revision FULL_REVIEWED_SOURCE_SHA --out /new/release --maven /path/to/mvn
```

Результат: project-manager.jar, release.json, SHA256SUMS, build.log. Проверить JAR SHA256
перед передачей и после копирования. Для сравнения сборок нужны те же JDK/Maven.
Не передавать исходники, БД и временные файлы на runtime VPS.

В settings.example.json задаются domain, app_root, app_user/group, java_bin, env_file,
auth_file, acme_webroot, tls_cert/key и ipv6. В demo.env.example — только PM_DEMO_PORT и
PM_DEMO_HEAP_MB. Нет секретов, SPRING_*, произвольных JAVA_OPTS и frontend-настроек порта.
Пути абсолютные Unix без пробелов/метасимволов, пользователь непривилегированный.
Домен без схемы, порта и wildcard; неизвестные/повторные параметры отвергаются.

```sh
python3 deploy/demo/package.py render --settings /path/settings.json --env /path/demo.env --out /new/bootstrap --bootstrap
python3 deploy/demo/package.py render --settings /path/settings.json --env /path/demo.env --out /new/https
```

В каждой поставке: unit, demo.env, settings.json, render.json, основной Nginx-конфиг,
проверенный bootstrap.conf, runtime-инструменты, шаблоны, README, тесты и полный SHA256SUMS.
HTTPS/ACME и IPv4/IPv6 генерируются из одной темы конфигурации. Выходной каталог должен
быть новым; существующие файлы не перезаписываются. При одинаковых входах байты одинаковы.
Перед административным исполнением установить весь проверенный bundle в root-owned каталог,
например /opt/project-manager-demo/packages/REVIEW_ID; файлы и все родительские каталоги
не должны быть доступны на запись группе/остальным. Не исполнять изменяемый пользователем
bundle из /home или /tmp под root. SHA256SUMS — контроль целостности, не цифровая подпись:
сверить его хеш с принятым review до передачи.

## Подготовка VPS и безопасный firewall

До изменений проверить хост, ОС/CPU/RAM/диск, SSH, sudo, Java, службы, полный список TCP/UDP
портов, IPv4/IPv6 маршруты и действующие virtual hosts. Конфликт домена/порта/путей — STOP.
Сохранить прежние конфиги. Не менять SSH, DNS, firewall провайдера и чужие приложения автоматически.
Пакеты для Ubuntu: openjdk-21-jre-headless nginx certbot apache2-utils python3; openssl,
curl, iproute2, systemd и runuser также должны быть доступны. Во время первой установки
предотвратить автозапуск стандартного Nginx-сайта: штатный policy-rc.d, сохранённый при наличии,
либо согласованный механизм ОС. После установки стандартный enabled-сайт отключить с backup,
Nginx оставить stopped/masked до проверенной bootstrap-конфигурации. Не публиковать default page.
Не устанавливать внешнюю БД и не обновлять major ОС.

UFW настраивать вручную только в отдельной разрешённой задаче. Для нового сервера с ранее
неактивным UFW: сохранить /etc/ufw и iptables/ip6tables/nft snapshots; подтвердить IPV6=yes,
реальный SSH-порт и внешний доступ/консоль провайдера. Оставить текущую административную
сессию открытой. Сначала поставить временный автоматический откат (`systemd-run` timer,
через 10 минут `ufw disable` только если UFW исходно был выключен), затем allow 22/tcp,
80/tcp, 443/tcp, deny incoming по умолчанию, allow outgoing и enable. Никогда не применять
этот откат к чужому уже активному firewall. Порт Java не разрешать.

Из второго независимого терминала проверить новое SSH-подключение (без multiplexing).
Только после его успеха из сохранённой сессии остановить timer отката и проверить UFW
IPv4/IPv6. При отсутствующем transient timer сначала проверить LoadState/list-units;
не считать ошибку неизвестной службы успешным подтверждением. Не закрывать исходную сессию
до проверки второй. Одноразовые firewall/bootstrap/cert-auth скрипты в автоматизацию не входят.

## Java: установка, guards и smoke

Создать системного пользователя/группу из settings без shell, home и sudo. При существующем
пользователе проверить UID, shell и группы вместо автоматического изменения. Создать root-owned
app_root/releases, app_root/packages, /etc/project-manager-demo и acme_webroot (0755).
Релиз разместить в app_root/releases/RELEASE_ID, root:root 0755; JAR/метаданные 0644.
Ни release/current, ни unit не заменять командами первой установки.

После проверки SHA256 передать JAR/release.json/SHA256SUMS в новый release. Перед первой
установкой unit и current проверить guards (bundle — абсолютный root-owned каталог):

```sh
sudo python3 /opt/project-manager-demo/packages/REVIEW_ID/operations.py preflight --bundle /opt/project-manager-demo/packages/REVIEW_ID --release /opt/project-manager-demo/releases/RELEASE_ID --revision FULL_REVIEWED_SOURCE_SHA --jar-sha256 REVIEWED_JAR_SHA256 --mode fresh
```

Проверяются provenance JAR, пользователь, полный inventory, свободный Java-порт,
masked+inactive Nginx, остановленное приложение, отсутствие current/unit. Для восстановления
уже проверенной первой установки `--mode retry` дополнительно требует неизменные unit/env,
правильный current, disabled service и MainPID=0. Команда только проверяет, не устанавливает
и не запускает службы. При FAIL не обходить guards; сначала диагностика. `--app-service`,
`--nginx-service`, `--unit` и пути executables можно задать явно; `--help` показывает параметры.

Установить demo.env root:root 0640 и unit root:root 0644 по значениям settings, создать current
на принятый release. Выполнить systemd-analyze verify ПЕРЕД daemon-reload и enable --now.
Повторно проверить, что Nginx всё ещё masked/inactive. Unit закрепляет classpath-only config,
127.0.0.1, demo/H2 memory/Console=false, сбрасывает JVM injection env, ограничивает права,
файловую запись и память. Все меры сохранены.

SuccessExitStatus=143 добавлен по PM-007.2: корректный SIGTERM остановленного Java давал exit 143
и ложный failed у systemd. Успешным дополнительно считается только код 143. Restart=on-failure,
ExitOnOutOfMemoryError и обработка всех остальных ошибочных кодов сохранены.

После запуска проверить status/journal, фактические UID/PID, UI, /api/projects,
/api/projects/{id}/dashboard, /api/health и 404 /h2-console/. MainPID из systemctl должен
совпадать с владельцем единственного app-listener. Получить **полный** ss -H -ltnp inventory;
validate_listeners() в bind_check.py допускает только 127.0.0.1 и его IPv4-mapped представление
[::ffff:127.0.0.1] на ожидаемом порту. Дополнительный порт того же PID, wildcard, внешний IP,
отсутствующий/смешанный owner — FAIL. ::1 и другие 127/8 не расширяют разрешённую bind-политику.
Не заменять этот контроль на поиск подстроки адреса/номера порта.

Локальный инструмент использует тот же парсер (Windows — полный Get-NetTCPConnection с PID,
Linux — полный ss). Он создаёт только отдельную memory DB, дважды запускает и останавливает
свои JVM, проверяет reset demo-данных и отрицательный datasource guard:

```sh
python3 deploy/demo/check_local.py --unit /path/generated/project-manager-demo.service --env /path/generated/demo.env --jar /path/project-manager.jar --out /new/local-results --java /path/to/java
```

Python -O/-OO запрещён для safety CLI. Это не проверка Linux systemd, HTTPS или firewall.

## ACME, Basic Auth и HTTPS

На выделенном Nginx сначала установить только bootstrap.conf как активный site. Проверить
nginx -t до unmask/start. Из внешней машины подтвердить challenge 200, / и /app.js и /api/projects
404: Java не опубликована. Перед ACME проверить NS/A/AAAA/CAA независимыми резолверами.
Если DNS/80 не готовы — остановиться, не запускать issuance и не публиковать HTTP backend.

```sh
sudo certbot certonly --webroot -w /configured/acme/webroot -d demo.example.com
sudo htpasswd -c -B /configured/auth/file pilot-user
```

ACME-почту/условия и пароль вводит владелец в терминале; htpasswd -b запрещён. -c только для
нового отсутствующего файла; не перезаписывать существующие пользователей. Права root:worker-group
0640; actual Nginx worker должен читать файл. Секреты/ключи остаются на сервере. Перед первым
publish создать root-owned каталог backup, например /var/backups/project-manager-demo 0700.

```sh
sudo python3 /opt/project-manager-demo/packages/REVIEW_ID/operations.py publish --bundle /opt/project-manager-demo/packages/REVIEW_ID --site /etc/nginx/conf.d/project-manager-demo.conf --backup /var/backups/project-manager-demo/acme-only.conf
```

Публикация проверяет полный SHA256SUMS, согласованность settings/env/rendered config, активный
loopback Java и MainPID, только известную текущую ACME/HTTPS-конфигурацию и отсутствие чужих
server-блоков. Проверяются сертификат/домен/срок, права htpasswd и чтение worker-пользователем.
Изолированный nginx -t точного HTTPS-файла выполняется до замены active site. ACME backup
создаётся эксклюзивно только из проверенного bootstrap; при повторном publish с HTTPS backup
обязан уже существовать и совпадать. HTTPS никогда не записывается вместо него.

После nginx -t/reload выполняются доверенный TLS handshake, 401 UI/JS/API без credentials и
с несуществующими probe-credentials, 401 POST/PATCH/PUT/DELETE, 404 диагностики и HTTP 308.
Настоящие credentials не входят в скрипт. Из внешней машины отдельно подтвердить сертификат,
HTTP redirect, UI/API Basic Auth, 18080 недоступен. Владелец вводит пароль только интерактивно
(например `curl --user pilot-user https://demo.example.com/api/projects`).

HTTPS_COMPLETE — только после всех проверок. При ошибке транзакция пытается восстановить
ACME: сравнение файла, nginx -t, успешный reload, 404 UI/JS/API, правильный challenge и отсутствие
любых listeners 443. Иначе выполняются обе операции stop+mask, проверяются inactive/failed,
masked и отсутствие 80/443. ROUTE_CLOSED выводится только при подтверждении одной из веток;
ошибка ss не означает пустой inventory. ROUTE_CLOSURE_NOT_CONFIRMED — авария, маршрут нельзя
считать закрытым; сохранить вывод и проверить сервер через доступ владельца/консоль.
Публикация в любом случае завершится ненулевым кодом при ошибке, даже после успешного rollback.
Для снятия пилота использовать ту же проверенную ACME-копию и алгоритм, не общий stop на чужих сайтах.

## TLS renewal

```sh
sudo python3 /opt/project-manager-demo/packages/REVIEW_ID/operations.py renewal --bundle /opt/project-manager-demo/packages/REVIEW_ID --site /etc/nginx/conf.d/project-manager-demo.conf
```

Инструмент устанавливает проверенный hook (nginx -t → reload → journal marker), включает
только штатный certbot.timer, проверяет enabled/active и выполняет
`certbot renew --dry-run --run-deploy-hooks --cert-name DOMAIN --non-interactive`.
Существующий другой hook автоматически не заменяется: STOP и отдельное ревью. Пути бинарников,
имя службы/таймера, hook и cert-name параметризованы; принудительного перевыпуска нет.
Действующий сертификат при dry-run не должен изменяться. Нет второго cron. Логи Certbot/таймера
и journal marker проверить после вызова. Реальное применение нового сертификата после будущего
renewal не объявлять проверенным только по dry-run. Документация Certbot уточняет, что с этим
флагом dry-run hook использует действующий сертификат, не staging-сертификат.

## Обновление JAR, возврат и reboot

Новый принятый JAR устанавливать в новый release, проверять SHA256. Сохранить current как previous,
подготовить новый symlink current.new и атомарно переключить current. Все временные ссылки должны
отсутствовать до операции, а разрешённые targets находиться внутри app_root/releases. Старый JAR
не удалять. Перед restart согласовать потерю demo-данных. Повторить local health/bind/Console и
публичные Auth/TLS проверки. При ошибке атомарно вернуть проверенный previous и restart; это возврат
бинарника, не данных. Если previous ещё не было, binary rollback NOT VERIFIED.

После отдельно разрешённого reboot: новое SSH, enabled/active app/Nginx/timer, UID/PID/полный
inventory loopback, nginx -t, внешний TLS/Auth/redirect/18080, восстановление seed-данных.
Реальную перезагрузку не выполнять как побочный эффект проверки пакета.
Журнал: `sudo journalctl -u project-manager-demo -n 100 --no-pager`;
остановка приложения: `sudo systemctl stop project-manager-demo`.

## Проверки пакета

```sh
python3 -B -m unittest discover -s deploy/demo -p 'test_*.py' -v
bash -n deploy/demo/https-transaction.sh
```

Те же тесты включены в generated bundle и запускаются `python3 -B -m unittest discover -s BUNDLE`.
Для Bash-тестов можно задать PM_DEMO_TEST_SHELL абсолютным путём к локальному Bash (в Windows —
Bash из Git). Они извлекают и выполняют действующие rollback-функции с имитациями команд и
не управляют реальными службами. Нужен Bash 4+, не произвольный POSIX shell.
Регрессии включают все 12 сценариев rollback, mapped bind/PID/полный inventory, guards,
SuccessExitStatus, manifests и детерминированный состав bundle. Nginx/openssl parser-тест
проверяет HTTPS/ACME × IPv4/IPv6 и отрицательную регрессию некавыченного `;`.
Он использует только временные сертификат/loopback-порты. При отсутствии инструментов SKIP —
это NOT VERIFIED, не PASS; нужна отдельная изолированная Linux-среда, не рабочий VPS.
Аналогично systemd-analyze verify на локальном Linux не заменяет реальный runtime systemd.

Первоисточники: [systemd.service](https://www.freedesktop.org/software/systemd/man/latest/systemd.service.html),
[Nginx](https://nginx.org/en/docs/beginners_guide.html),
[Certbot](https://eff-certbot.readthedocs.io/en/stable/using.html#renewing-certificates).