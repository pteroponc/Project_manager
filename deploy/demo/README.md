# Закрытый demo Project Manager: systemd + Nginx

Этот пакет готовится для ревью. Команды ниже выполняются на сервере только в отдельном
разрешённом задании. Здесь VPS не выбирается, не оплачивается, SSH-подключения и публикации нет.

## Что запускается

Один Java 21 JAR, systemd-служба project-manager-demo под отдельным pm-demo; Java слушает
только 127.0.0.1. Единственная внешняя точка приложения — Nginx HTTPS с Basic Auth на уровне
всего server: UI, ресурсы и все методы API. Файл паролей и TLS-ключи создаются на сервере,
не входят в Git/пакет. В приложении собственной подходящей аутентификации не найдено;
система пользователей не добавляется. Basic Auth завершает Nginx, Authorization в Java не передаётся.

Используется существующий профиль demo и IsolatedDatabaseGuard, зарегистрированный через
META-INF/spring.factories. Служба закрепляет профиль, разрешённый jdbc:h2:mem:pmdemo_vps,
выключенную H2 Console и create-drop. JNDI/Hikari-обходы по-прежнему отклоняются защитой.
Рабочая файловая БД, её копии, каталоги data/ и test DB сюда не переносятся.

ВАЖНО: каждый restart, аварийный перезапуск, update и rollback теряет введённые demo-данные.
Примеры создаются заново, их ID и относительные даты могут измениться. Это не постоянное
хранилище и не production-развёртывание.

## Что нужно от владельца

- VPS/провайдер, ОС и версия, архитектура, доступные CPU/RAM/диск; выделен ли сервер только пилоту.
- Домен, управление A/AAAA, наличие IPv6/CDN/других Nginx-сайтов и возможность HTTP-01 на TCP 80.
- Существующий способ административного доступа и ответственный за него; текущий SSH не менять.
- Кто установит Basic Auth-пользователей интерактивно и кто получит доступ к пилоту.
- Контакт для ACME, владелец продления, согласованные пути приложения, Java, сертификата и ключа.

Пример установки ниже рассчитан на Ubuntu 24.04 LTS, Java 21, Python 3.10+ и systemd с cgroups.
Это целевая схема, не заявление о проверке выбранного VPS. Для другой ОС сначала проверить
пакеты, пути исполняемых файлов и поддержку unit-директив. Сборочная машина: JDK 21 и Maven
3.9.9 (на VPS достаточно JRE). Nginx должен иметь HTTP SSL, proxy и auth_basic.

Минимальный практический ориентир для малого пилота: 1 vCPU, 2 GiB RAM, 10 GiB диска.
Это оценка вместе с ОС/Nginx/логами и несколькими JAR, не измеренная нагрузочная ёмкость.
Heap по умолчанию 512 MiB, MemoryMax службы 1 GiB. Фактические локальные измерения указаны
в отчёте проверок; Windows RSS не является измерением Linux/cgroups или гарантией числа пользователей.
H2 и списки растут в RAM, поэтому перед увеличением объёма нужна отдельная оценка.

## Сборка с фиксированного SHA

Не собирать с произвольного HEAD/ветки на сервере. package.py архивирует только pom.xml и src
указанного полного commit SHA в отдельный временный каталог, использует существующий Maven POM
и не делает checkout/reset/merge/stash/staging. По умолчанию SHA:
900113b54e14658ee7a0fc3ccae13e877ad78192.

~~~sh
python3 deploy/demo/package.py build --repo . --out /tmp/pm-demo-release-900113b
~~~

Если Maven не в PATH, передать --maven /absolute/path/to/mvn. На Windows допустим путь к mvn.cmd.
Выходной каталог не должен существовать. На выходе: project-manager.jar, release.json, SHA256SUMS,
build.log. project.build.outputTimestamp закреплён временем коммита. release.json фиксирует
JDK/Maven, исходный SHA и хеш JAR; для сравнения сборок использовать одинаковые версии инструментов.
Переносить один проверенный JAR с его метаданными. Для будущего принятого обновления разрешён
--revision с полным новым проверенным SHA; branch/ref сокращения отвергаются.

## Подготовка конфигураций

Скопировать settings.example.json и demo.env.example во внешний рабочий каталог и заполнить
НЕсекретные параметры. Домен pm-demo.example.com — только пример. Настоящие пароли/htpasswd,
сертификаты и приватные ключи не копировать в этот checkout или архив.

В settings.json задаются домен, app_root, Java, пользователь/группа, env_file, auth_file,
ACME webroot, пути сертификата/ключа, IPv6. В demo.env только PM_DEMO_PORT и PM_DEMO_HEAP_MB.
Порт один для systemd и Nginx: renderer читает общий env-файл. Нет JavaScript/frontend-настройки порта.
Непривилегированный порт 1024..65535, heap 128..512 MiB. Не добавлять SPRING_*, JAVA_OPTS
или секреты в env: renderer отклоняет неизвестные/повторные ключи и значения с метасимволами.

~~~sh
python3 deploy/demo/package.py render --settings /path/settings.json --env /path/demo.env --out /tmp/pm-demo-bootstrap --bootstrap
python3 deploy/demo/package.py render --settings /path/settings.json --env /path/demo.env --out /tmp/pm-demo-https
~~~

Bootstrap — временный ACME-only Nginx-конфиг без Java proxy и без слушателя 443.
В обычном режиме HTTP перенаправляет на HTTPS; единственное исключение без пароля —
статические ACME challenge-файлы. Для Java разрешены только loopback и memory datasource,
они закреплены аргументами unit и не вынесены в редактируемый env.
Рендерить в новый каталог; существующие выходные файлы не перезаписываются.

## Установка (будущее отдельное задание)

Команды-примеры соответствуют путям settings.example.json. При изменении путей/пользователей
использовать значения из render.json во всех командах. Существующие Nginx-конфиги и службы
не удалять; конфликты портов/доменов сначала разобрать. Здесь нет firewall-команд, изменения
SSH, отключения административного доступа или советов закрыть все порты.

~~~sh
sudo apt-get update
sudo apt-get install openjdk-21-jre-headless nginx apache2-utils certbot python3
java -version
nginx -V
sudo useradd --system --user-group --home-dir /nonexistent --shell /usr/sbin/nologin pm-demo
sudo install -d -o root -g root -m 0755 /opt/project-manager-demo/releases
sudo install -d -o root -g root -m 0755 /etc/project-manager-demo /var/lib/letsencrypt
sudo install -o root -g root -m 0640 /tmp/pm-demo-https/demo.env /etc/project-manager-demo/demo.env
sudo install -o root -g root -m 0644 /tmp/pm-demo-https/project-manager-demo.service /etc/systemd/system/project-manager-demo.service
~~~

Если пользователь уже существует, не менять его автоматически: проверить identity/groups и
отсутствие интерактивных/привилегированных прав. App root и releases принадлежат root; служба
может читать JAR, но не заменять его. Запись разрешена в отдельный /run/project-manager-demo.
Unit использует NoNewPrivileges, ProtectSystem, ProtectHome и отдельный runtime; не ослаблять
директивы для устранения ошибок без ревью.

Проверить SHA256 артефакта до переключения. Пример первой установки:
~~~sh
PM_RELEASE_DIR=/tmp/pm-demo-release-900113b
PM_APP_ROOT=/opt/project-manager-demo
PM_RELEASE_ID=900113b-review-1
(cd "$PM_RELEASE_DIR" && sha256sum -c SHA256SUMS)
sudo install -d -o root -g root -m 0755 "$PM_APP_ROOT/releases/$PM_RELEASE_ID"
sudo install -o root -g root -m 0644 "$PM_RELEASE_DIR/project-manager.jar" "$PM_APP_ROOT/releases/$PM_RELEASE_ID/project-manager.jar"
sudo install -o root -g root -m 0644 "$PM_RELEASE_DIR/release.json" "$PM_APP_ROOT/releases/$PM_RELEASE_ID/release.json"
sudo install -o root -g root -m 0644 "$PM_RELEASE_DIR/SHA256SUMS" "$PM_APP_ROOT/releases/$PM_RELEASE_ID/SHA256SUMS"
sudo ln -s "$PM_APP_ROOT/releases/$PM_RELEASE_ID" "$PM_APP_ROOT/current"
sudo systemd-analyze verify /etc/systemd/system/project-manager-demo.service
sudo systemctl daemon-reload
sudo systemctl enable --now project-manager-demo
~~~

Не заменять существующие release/current этими командами: для них раздел обновления.
Type=simple означает, что active сам по себе не доказывает HTTP-готовность. Дождаться ответа
локального health и проверить bind/процесс:
~~~sh
sudo systemctl status project-manager-demo --no-pager
sudo journalctl -u project-manager-demo -n 100 --no-pager
curl --fail --connect-timeout 2 --max-time 5 --retry 60 --retry-max-time 120 --retry-connrefused --retry-delay 1 http://127.0.0.1:18080/api/health
curl --fail http://127.0.0.1:18080/api/projects
sudo ss -ltnp
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18080/h2-console/
~~~

Java должна слушать только 127.0.0.1:18080 (или выбранный порт), не 0.0.0.0/::.
Console должна возвращать 404. Runtime под непривилегированным пользователем и unit sandbox
нужно проверить на реальном Linux: локальная проверка Windows их не доказывает.

## Basic Auth и первый TLS-сертификат

Создать пароль только интерактивно НА СЕРВЕРЕ; не использовать htpasswd -b, пароль в CLI,
в Git, параметрах JSON или скриншотах. Пример имени пользователя ниже — placeholder.
~~~sh
PM_AUTH_FILE=/etc/nginx/project-manager-demo.htpasswd
PM_PILOT_USER=pilot-review-user
sudo htpasswd -c -B "$PM_AUTH_FILE" "$PM_PILOT_USER"
sudo chown root:www-data "$PM_AUTH_FILE"
sudo chmod 0640 "$PM_AUTH_FILE"
~~~
Флаг -c только при первоначальном создании отсутствующего файла. Для добавления пользователя —
htpasswd -B без -c, иначе предыдущие записи будут заменены. Группу www-data сверить с worker Nginx.

Домен A должен указывать на выбранный VPS; AAAA публиковать только при настроенном и проверенном
IPv6 (ipv6=true). Входящие TCP 80 для ACME и 443 для пилота должны быть доступны по согласованной
политике провайдера/администратора. SSH и firewall этот пакет не меняет.
~~~sh
sudo install -o root -g root -m 0644 /tmp/pm-demo-bootstrap/project-manager-demo.conf /etc/nginx/conf.d/project-manager-demo.conf
sudo nginx -t
sudo systemctl reload nginx
PM_DOMAIN=pm-demo.example.com
PM_ACME_ROOT=/var/lib/letsencrypt
sudo certbot certonly --webroot -w "$PM_ACME_ROOT" -d "$PM_DOMAIN"
~~~

Ответственный вводит реальную ACME-почту и принимает условия непосредственно в Certbot.
Приложение ещё не опубликовано: bootstrap обслуживает только challenge, остальное 404.
Если 80/HTTP-01 недоступен, сначала согласовать другой способ выдачи; DNS API-ключи не входят в пакет.
После успешной выдачи сверить tls_cert/tls_key с фактическими путями, при необходимости заново render,
затем заменить ТОЛЬКО этот конфиг:
~~~sh
sudo cp /etc/nginx/conf.d/project-manager-demo.conf /etc/nginx/conf.d/project-manager-demo.conf.bootstrap-backup
sudo install -o root -g root -m 0644 /tmp/pm-demo-https/project-manager-demo.conf /etc/nginx/conf.d/project-manager-demo.conf
sudo nginx -t
sudo systemctl reload nginx
~~~
Если nginx -t не прошёл — не выполнять reload, вернуть предыдущий файл и проверить его. Не добавлять
app proxy в ACME-location и не отключать auth_basic для /api или ресурсов.

## Продление TLS

Использовать штатный Certbot timer (без второго cron) и deploy hook, который проверяет Nginx
и reload после успешного обновления сертификата. Пути nginx/systemctl сверить для выбранной ОС.
~~~sh
sudo install -d -o root -g root -m 0755 /etc/letsencrypt/renewal-hooks/deploy
sudo tee /etc/letsencrypt/renewal-hooks/deploy/project-manager-demo >/dev/null <<'SH'
#!/bin/sh
set -eu
/usr/sbin/nginx -t
/usr/bin/systemctl reload nginx
SH
sudo chmod 0750 /etc/letsencrypt/renewal-hooks/deploy/project-manager-demo
sudo systemctl enable --now certbot.timer
sudo systemctl list-timers certbot.timer
sudo certbot renew --dry-run
~~~
Проверить работу hook отдельно через sudo /etc/letsencrypt/renewal-hooks/deploy/project-manager-demo,
даты сертификата и отсутствие ошибок в журнале timer. Не считать dry-run проверкой app/systemd;
Java при продлении не перезапускается, demo-данные не сбрасываются.

## Внешняя проверка перед пилотом

Выполняется только после отдельного разрешения публикации. Без пароля UI, JS и все методы API —
401; с неверным паролем — 401; с правильным — ожидаемый ответ. Пароль curl вводит интерактивно,
не писать его после двоеточия в команде. Нельзя засчитать один успешный GET / за защиту всех API.
~~~sh
curl -s -o /dev/null -w '%{http_code}\n' "https://$PM_DOMAIN/"
curl -s -o /dev/null -w '%{http_code}\n' "https://$PM_DOMAIN/app.js"
curl -s -o /dev/null -w '%{http_code}\n' "https://$PM_DOMAIN/api/projects"
curl -s -X POST -H 'Content-Type: application/json' -d '{}' -o /dev/null -w '%{http_code}\n' "https://$PM_DOMAIN/api/projects"
curl -s -X PUT -H 'Content-Type: application/json' -d '{}' -o /dev/null -w '%{http_code}\n' "https://$PM_DOMAIN/api/projects/probe"
curl -s -X DELETE -o /dev/null -w '%{http_code}\n' "https://$PM_DOMAIN/api/projects/probe"
curl --fail --user "$PM_PILOT_USER" "https://$PM_DOMAIN/"
curl --fail --user "$PM_PILOT_USER" "https://$PM_DOMAIN/api/projects"
~~~
Проверки мутаций выше БЕЗ пароля и должны быть остановлены proxy с 401, не доходя до Java.
При любом ином результате остановить пилот. С правильным паролем /api/health, /h2-console/,
 /actuator/, /error и скрытые пути должны оставаться 404/недоступны. Из внешней машины проверить,
что Java-порт недоступен, HTTPS-цепочка/домен/срок верны, HTTP не отдаёт UI/API и нет второго
маршрута к Java в других Nginx-конфигах. Полный UI-прогон продукта здесь не повторяется.

## Обновление и возврат предыдущего JAR

Новый пакет собрать из нового принятого полного SHA, проверить SHA256 и установить в новый
не существующий releases/PM_RELEASE_ID аналогично первой установке (включая release.json/SHA256SUMS).
Не выполнять git pull/reset на runtime-каталоге, не удалять старые JAR. Перед переключением
согласовать потерю demo-данных. Ни current.new, ни previous.new не должны уже существовать;
если существуют — остановиться и разобрать причину.
~~~sh
PM_APP_ROOT=/opt/project-manager-demo
PM_RELEASE_ID=next-reviewed-release
PM_PREVIOUS=$(readlink -e "$PM_APP_ROOT/current")
case "$PM_PREVIOUS" in "$PM_APP_ROOT"/releases/*) ;; *) exit 1 ;; esac
test -r "$PM_PREVIOUS/project-manager.jar"
test -r "$PM_APP_ROOT/releases/$PM_RELEASE_ID/project-manager.jar"
(cd "$PM_APP_ROOT/releases/$PM_RELEASE_ID" && sha256sum -c SHA256SUMS)
sudo ln -s "$PM_PREVIOUS" "$PM_APP_ROOT/previous.new"
sudo mv -Tf "$PM_APP_ROOT/previous.new" "$PM_APP_ROOT/previous"
sudo ln -s "$PM_APP_ROOT/releases/$PM_RELEASE_ID" "$PM_APP_ROOT/current.new"
sudo mv -Tf "$PM_APP_ROOT/current.new" "$PM_APP_ROOT/current"
sudo systemctl restart project-manager-demo
~~~
После restart повторить локальные health/bind/console и HTTPS/Auth-проверки. Если запуск/проверки не прошли:
~~~sh
PM_ROLLBACK=$(readlink -e "$PM_APP_ROOT/previous")
case "$PM_ROLLBACK" in "$PM_APP_ROOT"/releases/*) ;; *) exit 1 ;; esac
(cd "$PM_ROLLBACK" && sha256sum -c SHA256SUMS)
sudo ln -s "$PM_ROLLBACK" "$PM_APP_ROOT/current.rollback"
sudo mv -Tf "$PM_APP_ROOT/current.rollback" "$PM_APP_ROOT/current"
sudo systemctl restart project-manager-demo
~~~
current.rollback также должен отсутствовать до команды. Это возврат бинарника, не восстановление данных:
после rollback будет новый пустой in-memory dataset с примерами. Пакет не меняет схему рабочей БД.

## Смена порта и остановка

Изменить PM_DEMO_PORT во внешнем demo.env, заново render в новый каталог; проверить сгенерированные
unit/Nginx. Установить demo.env, выполнить restart Java; установить Nginx-конфиг, nginx -t и reload.
Frontend остаётся прежним: все API-запросы same-origin, proxy_pass использует тот же env-порт.

~~~sh
sudo systemctl stop project-manager-demo
sudo systemctl disable project-manager-demo
~~~
Nginx при остановленном Java вернёт ошибку upstream только аутентифицированному клиенту.
Для вывода домена из пилота заменить только его конфиг на ACME-only или убрать только его
подключение после ревью, nginx -t/reload. Не останавливать общий Nginx с чужими сайтами и не менять SSH.
Удаление пользователей/артефактов/сертификатов не автоматизировано.

## Проверки пакета и первоисточники

~~~sh
python3 -B deploy/demo/test_package.py
~~~
Это проверки renderer и границ конфигурации, не systemd-analyze verify/nginx -t и не приёмка на VPS.
Статусы реальных локальных проверок и NOT VERIFIED — в deployment-validation.md в review ZIP.

Первичные документы: [systemd service](https://github.com/systemd/systemd/blob/main/man/systemd.service.xml),
[systemd exec](https://github.com/systemd/systemd/blob/main/man/systemd.exec.xml),
[Nginx Basic Auth](https://nginx.org/en/docs/http/ngx_http_auth_basic_module.html),
[Nginx proxy](https://nginx.org/en/docs/http/ngx_http_proxy_module.html),
[Certbot webroot/renewal](https://eff-certbot.readthedocs.io/en/stable/using.html).

Локальная lifecycle-проверка использует ExecStart из сгенерированного unit, заменяя только пути
Java/JAR/scratch для текущей ОС. Она запускает два JVM ПОСЛЕДОВАТЕЛЬНО, проверяет HTTP,
loopback, выключенную Console, сброс данных и отказ guard на запрещённом Hikari override.
Все её процессы останавливаются в finally. Это проверочная утилита, не второй способ установки.
Выбрать свободный PM_DEMO_PORT во внешнем env и новый выходной каталог:
~~~sh
python3 -B deploy/demo/check_local.py --unit /tmp/pm-demo-https/project-manager-demo.service --env /tmp/pm-demo-https/demo.env --jar /tmp/pm-demo-release-900113b/project-manager.jar --out /tmp/pm-demo-local-check
~~~
systemd, proxy, TLS и Basic Auth эта утилита не эмулирует и не засчитывает проверенными.
