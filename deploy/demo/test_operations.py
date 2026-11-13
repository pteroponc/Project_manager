#!/usr/bin/env python3
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import package as tool
import operations as ops

class OperationsTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='pm-demo-ops-test-')
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.settings=json.loads((tool.HERE/'settings.example.json').read_text())
        self.settings_file=self.root/'settings.json'
        self.settings_file.write_text(json.dumps(self.settings))
        self.env=self.root/'demo.env'
        self.env.write_text('PM_DEMO_PORT=18421\nPM_DEMO_HEAP_MB=512\n')
        self.bundle=self.root/'bundle'
        tool.render(self.settings_file,self.env,self.bundle)

    def test_complete_bundle_is_verifiable_and_deterministic(self):
        ops.verify_bundle(self.bundle)
        other=self.root/'other'
        tool.render(self.settings_file,self.env,other)
        self.assertEqual(sorted(x.name for x in self.bundle.iterdir()), sorted(x.name for x in other.iterdir()))
        for file in self.bundle.iterdir(): self.assertEqual(file.read_bytes(),(other/file.name).read_bytes(),file.name)
        for name in tool.SUPPORT_FILES: self.assertTrue((self.bundle/name).is_file())

    def test_checksum_detects_tamper_missing_and_unsafe_manifest(self):
        file=self.bundle/'https-transaction.sh'
        original=file.read_bytes();file.write_bytes(original+b'\n# tamper')
        with self.assertRaises(ValueError): ops.verify_bundle(self.bundle)
        file.write_bytes(original)
        manifest=self.bundle/'SHA256SUMS'
        text=manifest.read_text()
        for changed in [text+'0'*64+'  ../outside\n',text+text.splitlines()[0]+'\n',text.replace('check_local.py','absent.py')]:
            manifest.write_text(changed)
            with self.subTest(changed=changed[-80:]),self.assertRaises(ValueError): ops.verify_bundle(self.bundle)
        manifest.write_text(text)
        file.unlink()
        with self.assertRaises(ValueError): ops.verify_bundle(self.bundle)

    def test_metadata_and_configuration_cannot_drift(self):
        file=self.bundle/'project-manager-demo.conf'
        file.write_text(file.read_text().replace('127.0.0.1:18421','198.51.100.20:18421'))
        manifest=self.bundle/'SHA256SUMS'
        manifest.write_text('\n'.join(hashlib.sha256((self.bundle/line.split('  ')[1]).read_bytes()).hexdigest()+'  '+line.split('  ')[1] for line in manifest.read_text().splitlines())+'\n')
        with self.assertRaises(ValueError): ops.verify_bundle(self.bundle)

    def test_backup_never_overwritten_with_https_or_unknown_data(self):
        ops.backup_policy(b'acme',b'https',b'acme',None)
        ops.backup_policy(b'https',b'https',b'acme',b'acme')
        for live,backup in [(b'https',None),(b'acme',b'https'),(b'unknown',b'acme')]:
            with self.subTest(live=live,backup=backup),self.assertRaises(ValueError): ops.backup_policy(live,b'https',b'acme',backup)

    def test_service_names_and_paths_reject_injection(self):
        for value in ['nginx; id','nginx\nother','-nginx','../../nginx']:
            with self.assertRaises(ValueError): ops.service_name(value)
        for value in ['relative','/','/etc/../tmp','/etc/a;evil','/tmp/a\nnext']:
            with self.assertRaises(ValueError): ops.checked_path(value)

    def test_systemctl_failure_not_ignored_and_masked_return_is_recognized(self):
        host=ops.Host(SimpleNamespace(systemctl='/usr/bin/systemctl'))
        with patch.object(host,'run',return_value=SimpleNamespace(returncode=1,stdout='masked\n',stderr='')):
            self.assertEqual(host.ctl('is-enabled','nginx'),'masked')
        with patch.object(host,'run',return_value=SimpleNamespace(returncode=2,stdout='',stderr='bus failed')):
            with self.assertRaises(ValueError): host.ctl('is-active','nginx')

    def test_app_listener_checks_pid_and_other_ports(self):
        host=ops.Host(SimpleNamespace(systemctl='systemctl',ss='ss',app_service='demo'))
        row='LISTEN 0 100 [::ffff:127.0.0.1]:18421 *:* users:(("java",pid=1234,fd=1))'
        for inventory,valid in [(row,True),(row+'\n'+row.replace('18421','18422'),False),(row.replace('1234','4321'),False)]:
            with patch.object(host,'ctl',return_value='active'),patch.object(host,'run',side_effect=[SimpleNamespace(stdout='1234'),SimpleNamespace(stdout=inventory)]):
                if valid: self.assertEqual(host.app_listener(18421),row)
                else:
                    with self.assertRaises(ValueError): host.app_listener(18421)

    def preflight_fixture(self):
        app=self.root/'app';release=app/'releases'/'release-one'
        release.mkdir(parents=True)
        jar=b'isolated-test-fixture'
        (release/'project-manager.jar').write_bytes(jar)
        revision='a'*40;checksum=ops.digest(jar)
        (release/'release.json').write_text(json.dumps({'gitSha':revision,'jarSha256':checksum}))
        envfile=self.root/'installed.env';envfile.write_bytes((self.bundle/'demo.env').read_bytes())
        settings=dict(self.settings,app_root=str(app),env_file=str(envfile))
        args=SimpleNamespace(bundle=self.bundle,nginx_service='nginx',app_service='demo',release=str(release),revision=revision,jar_sha256=checksum,mode='fresh',unit=str(self.root/'demo.service'),ss='ss',systemctl='systemctl')
        host=ops.Host(args)
        return settings,args,host,release,app

    def test_fresh_guards_prevent_running_service_busy_port_existing_install(self):
        settings,args,host,release,app=self.preflight_fixture()
        def verify(_):return self.bundle,settings,{'PM_DEMO_PORT':18421},'https-basic-auth'
        for state,inventory,existing,valid in [('inactive','',False,True),('active','',False,False),('unknown','',False,False),('inactive','LISTEN 0 100 [::ffff:127.0.0.1]:18421 *:*',False,False),('inactive','',True,False)]:
            current=app/'current'
            if existing: current.mkdir()
            with self.subTest(state=state,busy=bool(inventory),existing=existing),patch.object(ops,'verify_bundle',side_effect=verify),patch.object(ops,'checked_path',side_effect=Path),patch.object(host,'inactive_masked'),patch.object(host,'account'),patch.object(host,'ctl',return_value=state),patch.object(host,'run',side_effect=lambda *c,**kw:SimpleNamespace(stdout='version \"21.0.1\"' if '-version' in c else inventory,stderr='')):
                if valid: ops.preflight(args,host)
                else:
                    with self.assertRaises(ValueError):ops.preflight(args,host)

    def test_retry_checks_exact_unit_pid_and_disabled_state(self):
        settings,args,host,release,app=self.preflight_fixture();args.mode='retry'
        Path(args.unit).write_bytes((self.bundle/'project-manager-demo.service').read_bytes())
        current=app/'current';resolve=Path.resolve
        for pid,enabled,valid in [('0','disabled',True),('123','disabled',False),('0','enabled',False)]:
            def run(*command,**kw):return SimpleNamespace(stdout='version \"21.0.1\"' if '-version' in command else pid if 'show' in command else '',stderr='')
            with self.subTest(pid=pid,enabled=enabled),patch.object(ops,'verify_bundle',return_value=(self.bundle,settings,{'PM_DEMO_PORT':18421},'https-basic-auth')),patch.object(ops,'checked_path',side_effect=Path),patch.object(host,'inactive_masked'),patch.object(host,'account'),patch.object(host,'ctl',side_effect=['inactive',enabled] if pid=='0' else ['inactive']),patch.object(host,'run',side_effect=run),patch.object(Path,'is_symlink',lambda x:x==current),patch.object(Path,'resolve',lambda x:release if x==current else resolve(x)):
                if valid:ops.preflight(args,host)
                else:
                    with self.assertRaises(ValueError):ops.preflight(args,host)

    def test_account_guard_rejects_root_login_and_admin_membership(self):
        host=ops.Host(SimpleNamespace())
        for uid,shell,admin,valid in [(100,'/usr/sbin/nologin',False,True),(0,'/usr/sbin/nologin',False,False),(100,'/bin/bash',False,False),(100,'/usr/sbin/nologin',True,False)]:
            user=SimpleNamespace(pw_uid=uid,pw_gid=100,pw_shell=shell,pw_name='pm-demo')
            group=SimpleNamespace(gr_name='sudo',gr_gid=27,gr_mem=['pm-demo'] if admin else [])
            modules={'pwd':SimpleNamespace(getpwnam=lambda _:user),'grp':SimpleNamespace(getgrnam=lambda _:SimpleNamespace(gr_gid=100),getgrall=lambda:[group])}
            with self.subTest(uid=uid,shell=shell,admin=admin),patch.dict('sys.modules',modules):
                if valid:host.account(self.settings)
                else:
                    with self.assertRaises(ValueError):host.account(self.settings)

    def test_unit_143_does_not_mark_other_failures_successful(self):
        unit=(self.bundle/'project-manager-demo.service').read_text()
        self.assertEqual([x for x in unit.splitlines() if x.startswith('SuccessExitStatus=')],['SuccessExitStatus=143'])
        self.assertIn('Restart=on-failure\n',unit)
        self.assertIn('-XX:+ExitOnOutOfMemoryError',unit)
        self.assertNotIn('RestartPreventExitStatus=',unit)

    def test_hook_tests_config_before_reload_and_has_no_issuance_or_cron(self):
        hook=(self.bundle/'nginx-deploy-hook.sh.in').read_text()
        self.assertLess(hook.index('__NGINX_BIN__ -t'),hook.index('__SYSTEMCTL_BIN__ reload'))
        self.assertIn('set -eu',hook)
        self.assertNotIn('certbot',hook)
        self.assertNotIn('cron',hook)


    def publication_fixture(self):
        site=self.root/'site.conf';site.write_bytes((self.bundle/'bootstrap.conf').read_bytes())
        backup=self.root/'acme-backup.conf'
        auth=self.root/'auth-file';auth.write_text('test-only-fixture')
        acme=self.root/'acme';(acme/'.well-known/acme-challenge').mkdir(parents=True)
        cert=self.root/'certificate';cert.write_bytes(b'test-certificate')
        settings=dict(self.settings,auth_file=str(auth),acme_webroot=str(acme),tls_cert=str(cert))
        args=SimpleNamespace(bundle=self.bundle,site=str(site),backup=str(backup),nginx_service='custom-nginx',nginx='nginx',runuser='runuser',openssl='openssl',bash='bash',systemctl='systemctl',ss='ss',curl='curl',certbot='certbot',certbot_timer='certbot.timer',cert_name=None,hook=str(self.root/'hooks/deploy'),logger='logger',journalctl='journalctl')
        return settings,args,site,backup,auth,acme

    def test_publish_prevalidates_before_activation_and_keeps_acme_snapshot(self):
        for fail in [None,'certificate','isolated-parser']:
            with self.subTest(fail=fail),tempfile.TemporaryDirectory() as isolated:
                # Each branch gets its own fixture to avoid relying on leftovers.
                original_root=self.root;self.root=Path(isolated)
                settings,args,site,backup,auth,acme=self.publication_fixture()
                host=ops.Host(args);calls=[]
                def run(*command,**kw):
                    calls.append(command)
                    if '-T' in command:return SimpleNamespace(stdout='user www-data;\n'+site.read_text())
                    if fail=='certificate' and command[0]=='openssl':raise ValueError('certificate rejected')
                    if fail=='isolated-parser' and '-p' in command:raise ValueError('parser rejected')
                    return SimpleNamespace(stdout='')
                real_stat=Path.stat
                def stat(file,*a,**kw):
                    if file==auth:return SimpleNamespace(st_uid=0,st_mode=0o100640)
                    return real_stat(file,*a,**kw)
                with patch.object(ops,'verify_bundle',return_value=(self.bundle,settings,{'PM_DEMO_PORT':18421},'https-basic-auth')),patch.object(ops,'checked_path',side_effect=Path),patch.object(ops,'trusted_path'),patch.object(Path,'stat',stat),patch.object(host,'app_listener'),patch.object(host,'ctl',return_value='active'),patch.object(host,'run',side_effect=run),patch.object(ops.subprocess,'run',return_value=SimpleNamespace(returncode=0)) as transaction:
                    if fail:
                        with self.assertRaises(ValueError):ops.publish(args,host)
                        transaction.assert_not_called();self.assertFalse(backup.exists())
                    else:
                        ops.publish(args,host)
                        self.assertTrue(any('-p' in call for call in calls))
                        transaction.assert_called_once()
                        self.assertEqual(backup.read_bytes(),(self.bundle/'bootstrap.conf').read_bytes())
                        self.assertIn('custom-nginx',transaction.call_args.args[0])
                        self.assertNotIn('BASH_ENV',transaction.call_args.kwargs['env'])
                    self.assertEqual(site.read_bytes(),(self.bundle/'bootstrap.conf').read_bytes())
                    self.assertFalse((acme/'.well-known/acme-challenge/pm-demo-publish-probe').exists())
                self.root=original_root

    def test_renewal_requires_timer_and_successful_hook_evidence(self):
        settings,args,site,backup,auth,acme=self.publication_fixture()
        site.write_bytes((self.bundle/'project-manager-demo.conf').read_bytes())
        for marker,valid in [('PM_DEMO_TLS_DEPLOY_HOOK_RELOAD_OK',True),('',False)]:
            calls=[];host=ops.Host(args)
            def run(*command,**kw):
                calls.append(command)
                if '--help' in command:return SimpleNamespace(stdout='--run-deploy-hooks',stderr='')
                if command[0]=='journalctl':return SimpleNamespace(stdout=marker,stderr='')
                return SimpleNamespace(stdout='',stderr='')
            with self.subTest(marker=marker),patch.object(ops,'verify_bundle',return_value=(self.bundle,settings,{},'https-basic-auth')),patch.object(ops,'checked_path',side_effect=Path),patch.object(ops,'trusted_path'),patch.object(host,'run',side_effect=run),patch.object(host,'ctl',side_effect=['enabled','active']):
                if valid:ops.renewal(args,host)
                else:
                    with self.assertRaises(ValueError):ops.renewal(args,host)
                self.assertIn(('certbot','renew','--dry-run','--run-deploy-hooks','--cert-name',settings['domain'],'--non-interactive'),calls)
                self.assertFalse(any('--force-renewal' in call for call in calls))
                self.assertEqual(Path(settings['tls_cert']).read_bytes(),b'test-certificate')

    def test_different_existing_hook_is_not_overwritten(self):
        settings,args,site,backup,auth,acme=self.publication_fixture()
        site.write_bytes((self.bundle/'project-manager-demo.conf').read_bytes())
        hook=Path(args.hook);hook.parent.mkdir();hook.write_text('existing owner hook')
        host=ops.Host(args)
        with patch.object(ops,'verify_bundle',return_value=(self.bundle,settings,{},'https-basic-auth')),patch.object(ops,'checked_path',side_effect=Path),patch.object(ops,'trusted_path'),patch.object(host,'run',return_value=SimpleNamespace(stdout='--run-deploy-hooks')):
            with self.assertRaises(ValueError):ops.renewal(args,host)
        self.assertEqual(hook.read_text(),'existing owner hook')

if __name__=='__main__':unittest.main(verbosity=2)