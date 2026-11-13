#!/usr/bin/env python3
"""Linux operations for a verified, dedicated demo host. Never contacts a remote host."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime, timezone

import package as tool
from bind_check import validate_listeners


def digest(data):
    return hashlib.sha256(data).hexdigest()


def checked_path(value):
    if not re.fullmatch(r'/[A-Za-z0-9_./+-]+', value) or value == '/' or '..' in Path(value).parts:
        raise ValueError('Unsafe absolute path: ' + value)
    return Path(value)


def trusted_path(path):
    for parent in [path, *path.parents]:
        if parent.exists() and (parent.is_symlink() or parent.stat().st_uid != 0 or parent.stat().st_mode & 0o022):
            raise ValueError('Administrative path must be root-controlled: ' + str(parent))


def service_name(value):
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.@-]*', value):
        raise ValueError('Invalid service name')
    return value


def verify_bundle(directory):
    directory = Path(directory).resolve()
    entries = {}
    for line in (directory / 'SHA256SUMS').read_text().splitlines():
        match = re.fullmatch(r'([0-9a-f]{64})  ([A-Za-z0-9_.-]+)', line)
        if not match or match[2] in entries:
            raise ValueError('Malformed or duplicate manifest entry')
        entries[match[2]] = match[1]
    expected = set(tool.SUPPORT_FILES) | {'project-manager-demo.conf', 'bootstrap.conf',
                'settings.json', 'project-manager-demo.service', 'render.json', 'demo.env'}
    if set(entries) != expected:
        raise ValueError('Bundle manifest does not cover every required tool/config')
    for name, checksum in entries.items():
        file = directory / name
        if file.is_symlink() or not file.is_file() or digest(file.read_bytes()) != checksum:
            raise ValueError('Checksum/type mismatch: ' + name)
    settings = tool.read_settings(directory / 'settings.json')
    env = tool.read_environment(directory / 'demo.env')
    meta = json.loads((directory / 'render.json').read_text())
    if meta != {'mode': meta.get('mode'), 'settings': settings, 'environment': env} or meta['mode'] not in ['https-basic-auth', 'bootstrap-acme-only']:
        raise ValueError('Inconsistent render metadata')
    # Re-render trusted templates with validated settings; checksums alone are not policy validation.
    with tempfile.TemporaryDirectory(prefix='pm-demo-consistency-') as temp:
        regenerated = tool.render(directory / 'settings.json', directory / 'demo.env',
                                  Path(temp) / 'rendered', meta['mode'] == 'bootstrap-acme-only')
        for name in ['project-manager-demo.conf', 'bootstrap.conf', 'project-manager-demo.service']:
            if (directory / name).read_text() != regenerated[name]:
                raise ValueError('Generated configuration differs from validated settings: ' + name)
    return directory, settings, env, meta['mode']


class Host:
    def __init__(self, args):
        self.args = args

    def run(self, *command, check=True):
        return subprocess.run(list(map(str, command)), text=True, capture_output=True, check=check)

    def ctl(self, action, service):
        result = self.run(self.args.systemctl, action, service, check=False)
        if result.returncode not in [0, 1, 3, 4]:
            raise ValueError('systemctl failed: ' + result.stderr)
        return result.stdout.strip()

    def inactive_masked(self, service):
        if self.ctl('is-enabled', service) != 'masked' or self.ctl('is-active', service) != 'inactive':
            raise ValueError('STOP: Nginx must be masked and inactive')

    def account(self, settings):
        import pwd
        import grp
        user = pwd.getpwnam(settings['app_user'])
        if user.pw_uid == 0 or user.pw_shell not in ['/usr/sbin/nologin', '/sbin/nologin', '/bin/false']:
            raise ValueError('Unsafe application account')
        if grp.getgrnam(settings['app_group']).gr_gid != user.pw_gid:
            raise ValueError('Unexpected primary application group')
        for group in grp.getgrall():
            if group.gr_name in ['sudo', 'wheel'] and (user.pw_name in group.gr_mem or user.pw_gid == group.gr_gid):
                raise ValueError('Application account must not have administrative membership')

    def app_listener(self, port):
        if self.ctl('is-active', self.args.app_service) != 'active':
            raise ValueError('Application is not active')
        pid = int(self.run(self.args.systemctl, 'show', self.args.app_service, '-p', 'MainPID', '--value').stdout.strip())
        return validate_listeners(self.run(self.args.ss, '-H', '-ltnp').stdout, pid, port)


def preflight(args, host):
    bundle, settings, env, _ = verify_bundle(args.bundle)
    host.inactive_masked(args.nginx_service)
    host.account(settings)
    java = host.run(settings['java_bin'], '-version')
    if not re.search(r'version "21[."]', java.stdout + java.stderr):
        raise ValueError('Java 21 required')
    release = checked_path(args.release).resolve()
    app_root = Path(settings['app_root'])
    if release.parent != app_root / 'releases':
        raise ValueError('Release must be one child of app_root/releases')
    if not re.fullmatch('[0-9a-f]{40}', args.revision) or not re.fullmatch('[0-9a-f]{64}', args.jar_sha256):
        raise ValueError('Explicit reviewed revision and JAR SHA256 required')
    metadata = json.loads((release / 'release.json').read_text())
    if metadata['gitSha'] != args.revision or metadata['jarSha256'] != args.jar_sha256 or digest((release / 'project-manager.jar').read_bytes()) != args.jar_sha256:
        raise ValueError('Release provenance/hash mismatch')
    if host.ctl('is-active', args.app_service) not in ['inactive', 'failed']:
        raise ValueError('STOP: application is running or its state is unknown')
    # Parse every row, including IPv4-mapped addresses; a busy expected port always fails.
    from bind_check import parse_endpoint
    for row in host.run(args.ss, '-H', '-ltnp').stdout.splitlines():
        fields = row.split()
        if len(fields) < 5 or fields[0] != 'LISTEN':
            raise ValueError('Unrecognized listener inventory')
        if parse_endpoint(fields[3])[1] == env['PM_DEMO_PORT']:
            raise ValueError('STOP: Java port is already occupied')
    current = app_root / 'current'
    unit = checked_path(args.unit)
    if args.mode == 'fresh':
        if current.exists() or current.is_symlink() or unit.exists() or unit.is_symlink():
            raise ValueError('STOP: existing installation requires explicit retry/update procedure')
    else:
        if not current.is_symlink() or current.resolve() != release:
            raise ValueError('STOP: retry does not match installed current release')
        if unit.is_symlink() or unit.read_bytes() != (bundle / 'project-manager-demo.service').read_bytes():
            raise ValueError('STOP: installed unit differs from verified bundle')
        if Path(settings['env_file']).read_bytes() != (bundle / 'demo.env').read_bytes():
            raise ValueError('STOP: installed environment differs')
        pid = host.run(args.systemctl, 'show', args.app_service, '-p', 'MainPID', '--value').stdout.strip()
        if pid != '0' or host.ctl('is-enabled', args.app_service) != 'disabled':
            raise ValueError('STOP: retry requires disabled service with PID 0')
    print('PREFLIGHT_PASS: no installation changes made')


def backup_policy(live, https, bootstrap, backup):
    if live not in [https, bootstrap]:
        raise ValueError('STOP: unknown current site configuration')
    if backup is not None and backup != bootstrap:
        raise ValueError('STOP: existing ACME backup is not the verified ACME-only file')
    if live == https and backup is None:
        raise ValueError('STOP: cannot create ACME backup from HTTPS configuration')


def publish(args, host):
    bundle, settings, env, mode = verify_bundle(args.bundle)
    if mode != 'https-basic-auth':
        raise ValueError('HTTPS bundle required')
    host.app_listener(env['PM_DEMO_PORT'])
    if host.ctl('is-active', args.nginx_service) != 'active':
        raise ValueError('Nginx must already serve the reviewed ACME-only or HTTPS site')
    site, backup = checked_path(args.site), checked_path(args.backup)
    if site == backup or site.is_symlink() or backup.is_symlink():
        raise ValueError('Site/backup must be separate regular files')
    trusted_path(site)
    trusted_path(backup)
    https = (bundle / 'project-manager-demo.conf').read_bytes()
    bootstrap = (bundle / 'bootstrap.conf').read_bytes()
    backup_policy(site.read_bytes(), https, bootstrap, backup.read_bytes() if backup.exists() else None)
    effective = host.run(args.nginx, '-T').stdout
    # stop+mask is safe only on the dedicated Nginx described by this bundle.
    expected_servers = len(re.findall(rb'^server \{', site.read_bytes(), re.M))
    if len(re.findall(r'^\s*server\s*\{', effective, re.M)) != expected_servers:
        raise ValueError('STOP: other Nginx sites present; automatic service stop is not safe')
    worker = re.search(r'^user\s+([a-z_][a-z0-9_-]*)(?:\s+[a-z_][a-z0-9_-]*)?;', effective, re.M)
    if not worker or worker[1] == 'root':
        raise ValueError('Cannot determine unprivileged Nginx worker identity')
    auth = Path(settings['auth_file'])
    if auth.is_symlink() or auth.stat().st_uid != 0 or auth.stat().st_mode & 0o777 != 0o640:
        raise ValueError('Auth file must be root-owned 0640')
    host.run(args.runuser, '-u', worker[1], '--', 'test', '-r', auth)
    host.run(args.openssl, 'x509', '-in', settings['tls_cert'], '-noout', '-checkhost', settings['domain'])
    host.run(args.openssl, 'x509', '-in', settings['tls_cert'], '-noout', '-checkend', '604800')
    with tempfile.TemporaryDirectory(prefix='pm-demo-https-') as temp:
        temp = Path(temp)
        candidate = temp / 'candidate.conf'
        candidate.write_bytes(https)
        wrapper = temp / 'nginx.conf'
        wrapper.write_text(f'error_log stderr;\npid {temp}/nginx.pid;\nevents {{}}\nhttp {{ access_log off; include {candidate}; }}\n')
        host.run(args.nginx, '-t', '-e', 'stderr', '-p', temp, '-c', wrapper)
        if not backup.exists():
            with backup.open('xb') as file:
                file.write(bootstrap)
            backup.chmod(0o600)
        probe = Path(settings['acme_webroot']) / '.well-known/acme-challenge/pm-demo-publish-probe'
        with probe.open('x', encoding='ascii') as file:
            file.write('PM_DEMO_ACME_ONLY\n')
        probe.chmod(0o644)
        try:
            # subprocess is deliberately not check=True: the shell has already performed and verified rollback on failure.
            clean_env = os.environ.copy()
            for name in ['BASH_ENV', 'ENV', 'SHELLOPTS', 'CDPATH']:
                clean_env.pop(name, None)
            result = subprocess.run([args.bash, str(bundle / 'https-transaction.sh'),
                       str(candidate), str(site), str(backup), settings['domain'], args.nginx_service,
                       args.nginx, args.systemctl, args.ss, args.curl, sys.executable,
                       str(bundle / 'verify_https.py')], check=False, env=clean_env)
            if result.returncode:
                raise ValueError('Publication failed; inspect confirmed/unconfirmed route marker above')
        finally:
            # Delete only the one probe exclusively created by this invocation.
            probe.unlink()


def renewal(args, host):
    bundle, settings, _, mode = verify_bundle(args.bundle)
    if mode != 'https-basic-auth':
        raise ValueError('HTTPS bundle required')
    if Path(args.site).read_bytes() != (bundle / 'project-manager-demo.conf').read_bytes():
        raise ValueError('Current HTTPS configuration differs')
    host.run(args.nginx, '-t')
    help_text = host.run(args.certbot, '--help', 'all').stdout
    if '--run-deploy-hooks' not in help_text:
        raise ValueError('Installed Certbot lacks dry-run deploy hooks')
    template = (bundle / 'nginx-deploy-hook.sh.in').read_text()
    for name, value in [('NGINX_BIN', args.nginx), ('SYSTEMCTL_BIN', args.systemctl),
                        ('NGINX_SERVICE', args.nginx_service), ('LOGGER_BIN', args.logger)]:
        template = template.replace('__' + name + '__', value)
    hook = checked_path(args.hook)
    trusted_path(hook)
    if hook.is_symlink() or (hook.exists() and hook.read_text() != template):
        raise ValueError('STOP: existing deploy-hook differs; review it before replacement')
    if not hook.exists():
        hook.parent.mkdir(parents=True, exist_ok=True, mode=0o755)
        with hook.open('x') as file:
            file.write(template)
        hook.chmod(0o750)
    cert_before = Path(settings['tls_cert']).read_bytes()
    started = datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')
    host.run(args.systemctl, 'enable', '--now', args.certbot_timer)
    if host.ctl('is-enabled', args.certbot_timer) != 'enabled' or host.ctl('is-active', args.certbot_timer) != 'active':
        raise ValueError('Certbot timer is not enabled/active')
    result = host.run(args.certbot, 'renew', '--dry-run', '--run-deploy-hooks',
                      '--cert-name', args.cert_name or settings['domain'], '--non-interactive')
    print(result.stdout, result.stderr)
    journal = host.run(args.journalctl, '-t', 'project-manager-demo-tls', '--since', started, '--no-pager').stdout
    if 'PM_DEMO_TLS_DEPLOY_HOOK_RELOAD_OK' not in journal:
        raise ValueError('No successful deploy-hook reload marker for this dry-run')
    print(journal)
    if Path(settings['tls_cert']).read_bytes() != cert_before:
        raise ValueError('Installed certificate unexpectedly changed during dry-run')
    host.run(args.nginx, '-t')
    print(host.run(args.systemctl, 'list-timers', '--all', args.certbot_timer).stdout)
    print('RENEWAL_DRY_RUN_PASS: real future renewal remains NOT VERIFIED')


def main():
    if not __debug__:
        raise ValueError('Safety operations must not run under Python -O')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['preflight', 'publish', 'renewal'])
    parser.add_argument('--bundle', required=True)
    parser.add_argument('--site', default='/etc/nginx/conf.d/project-manager-demo.conf')
    parser.add_argument('--backup', default='/var/backups/project-manager-demo/acme-only.conf')
    parser.add_argument('--app-service', type=service_name, default='project-manager-demo')
    parser.add_argument('--nginx-service', type=service_name, default='nginx')
    parser.add_argument('--nginx', default='/usr/sbin/nginx')
    parser.add_argument('--systemctl', default='/usr/bin/systemctl')
    parser.add_argument('--ss', default='/usr/bin/ss')
    parser.add_argument('--curl', default='/usr/bin/curl')
    parser.add_argument('--openssl', default='/usr/bin/openssl')
    parser.add_argument('--runuser', default='/usr/sbin/runuser')
    parser.add_argument('--bash', default='/bin/bash')
    parser.add_argument('--certbot', default='/usr/bin/certbot')
    parser.add_argument('--journalctl', default='/usr/bin/journalctl')
    parser.add_argument('--logger', default='/usr/bin/logger')
    parser.add_argument('--hook', default='/etc/letsencrypt/renewal-hooks/deploy/project-manager-demo')
    parser.add_argument('--certbot-timer', type=service_name, default='certbot.timer')
    parser.add_argument('--cert-name', type=service_name)
    parser.add_argument('--release')
    parser.add_argument('--revision')
    parser.add_argument('--jar-sha256')
    parser.add_argument('--unit', default='/etc/systemd/system/project-manager-demo.service')
    parser.add_argument('--mode', choices=['fresh', 'retry'], default='fresh')
    args = parser.parse_args()
    if sys.platform != 'linux' or os.geteuid() != 0:
        raise ValueError('Operations require Linux root; tests must use command doubles, never the live server')
    for name in ['site','backup','nginx','systemctl','ss','curl','openssl','runuser','bash','certbot','logger','journalctl','hook','unit']:
        checked_path(getattr(args, name))
    if args.action == 'preflight' and not all([args.release, args.revision, args.jar_sha256]):
        raise ValueError('preflight requires release, revision and jar-sha256')
    directory = Path(args.bundle).resolve()
    trusted_path(directory)
    args.bundle = str(directory)
    # Administrative code must not be mutable by unprivileged users during execution.
    for path in [directory, *directory.iterdir()]:
        if path.is_symlink() or path.stat().st_uid != 0 or path.stat().st_mode & 0o022:
            raise ValueError('Install the reviewed bundle as root-owned and not group/world writable')
    {'preflight': preflight, 'publish': publish, 'renewal': renewal}[args.action](args, Host(args))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, subprocess.SubprocessError) as error:
        raise SystemExit('STOP: ' + str(error))