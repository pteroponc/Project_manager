#!/usr/bin/env python3
import os,pathlib,re,shutil,subprocess,unittest,sys,json
from unittest.mock import patch
from types import SimpleNamespace
from operations import Host
import check_local
from bind_check import parse_endpoint, is_loopback_endpoint, validate_listeners, validate_windows_listeners

PID=1816
def row(endpoint="127.0.0.1:18080",pid=PID):
    return 'LISTEN 0 100 '+endpoint+' *:* users:(("java",pid='+str(pid)+',fd=11))'

class BindCheckTest(unittest.TestCase):
    def test_ipv4_and_mapped_representations(self):
        for host in ["127.0.0.1","::ffff:127.0.0.1","[::ffff:127.0.0.1]",
                     "[0:0:0:0:0:ffff:7f00:1]","::FFFF:7f00:1"]:
            with self.subTest(host=host):
                text=row(host+":18080")
                self.assertEqual(validate_listeners(text,PID),text)
    def test_original_production_sample(self):
        text='LISTEN 0 100 [::ffff:127.0.0.1]:18080 *:* users:(("java",pid=1816,fd=11))'
        self.assertNotEqual(text.split()[3],"127.0.0.1:18080")
        self.assertEqual(validate_listeners(text,PID),text)
    def test_wildcards_and_external_addresses_are_rejected(self):
        for host in ["0.0.0.0","[::]","*","198.51.100.20","10.0.0.1","192.168.1.1",
                     "[2001:db8::20]","[::ffff:198.51.100.20]","[::ffff:0.0.0.0]"]:
            with self.subTest(host=host), self.assertRaises(ValueError):
                validate_listeners(row(host+":18080"),PID)
    def test_other_loopbacks_are_not_expanded_into_bind_policy(self):
        for host in ["127.0.0.2","127.1.2.3","[::1]","[::ffff:127.0.0.2]","[::127.0.0.1]"]:
            with self.subTest(host=host), self.assertRaises(ValueError):
                validate_listeners(row(host+":18080"),PID)
    def test_missing_or_duplicate_listeners_fail(self):
        for text in ["",row()+"\n"+row(),row()+"\n"+row("[::ffff:127.0.0.1]:18080")]:
            with self.subTest(text=text), self.assertRaises(ValueError):
                validate_listeners(text,PID)
    def test_additional_application_socket_on_unexpected_port_fails(self):
        for endpoint in ["127.0.0.1:18081","0.0.0.0:8080","[::1]:18081"]:
            with self.subTest(endpoint=endpoint), self.assertRaises(ValueError):
                validate_listeners(row()+"\n"+row(endpoint),PID)
    def test_only_wrong_application_port_fails(self):
        with self.assertRaises(ValueError): validate_listeners(row("127.0.0.1:18081"),PID)
    def test_other_process_on_expected_port_fails(self):
        for text in [row(pid=18160),row()+"\n"+row(pid=9999)]:
            with self.subTest(text=text), self.assertRaises(ValueError):
                validate_listeners(text,PID)
    def test_missing_or_shared_owner_fails(self):
        for text in [row().replace(' users:(("java",pid=1816,fd=11))',""),
                     row().replace("pid=1816,","pid=9999,"),
                     row()+',("other",pid=9999,fd=3)']:
            with self.subTest(text=text), self.assertRaises(ValueError):
                validate_listeners(text,PID)
    def test_unrelated_ssh_listeners_are_preserved_and_ignored(self):
        inventory=row("0.0.0.0:22",648)+"\n"+row("[::]:22",648)+"\n"+row()
        self.assertEqual(validate_listeners(inventory,PID),row())
    def test_invalid_ss_and_pid_fail_closed(self):
        for pid in (0,-1,True,"1816"):
            with self.subTest(pid=pid), self.assertRaises(ValueError):
                validate_listeners(row(),pid)
        for text in ["LISTEN","State Recv-Q Send-Q Local Peer Process",row().replace("LISTEN","ESTAB")]:
            with self.subTest(text=text), self.assertRaises(ValueError):
                validate_listeners(text,PID)
    def test_malformed_endpoints_do_not_trigger_dns_or_loose_parsing(self):
        for endpoint in ["localhost:18080","127.0.0.1:http","127.0.0.1:018080","127.0.0.1:0",
                         "127.0.0.1:65536","[::ffff:127.0.0.1:18080","127.0.0.1]:18080",
                         "127.0.0.1%eth0:18080","::ffff:127.0.0.1%lo:18080"]:
            with self.subTest(endpoint=endpoint), self.assertRaises(ValueError):
                parse_endpoint(endpoint)
    def test_mapped_loopback_connections_are_normalized(self):
        for endpoint in ["127.0.0.1:45678","[::1]:45678","[::ffff:127.0.0.1]:45678"]:
            with self.subTest(endpoint=endpoint): self.assertTrue(is_loopback_endpoint(endpoint))
        for endpoint in ["198.51.100.20:5432","[::ffff:198.51.100.20]:5432","0.0.0.0:5432","[::]:5432"]:
            with self.subTest(endpoint=endpoint): self.assertFalse(is_loopback_endpoint(endpoint))
    def test_nginx_guard_fails_closed_for_active_and_unknown_states(self):
        for state, enabled, valid in [('active','masked',False),('inactive','masked',True),
                                      ('unknown','masked',False),('inactive','enabled',False)]:
            host=Host(SimpleNamespace())
            with self.subTest(state=state,enabled=enabled), patch.object(host,'ctl',side_effect=[enabled,state]):
                if valid: host.inactive_masked('example-nginx')
                else:
                    with self.assertRaises(ValueError): host.inactive_masked('example-nginx')

    def test_windows_inventory_and_extra_socket(self):
        good={'LocalAddress':'::ffff:127.0.0.1','LocalPort':18080,'OwningProcess':PID}
        self.assertIn('127.0.0.1:18080',validate_windows_listeners([good],PID,18080))
        for other in [dict(good,LocalPort=18081),dict(good,OwningProcess=0),dict(good,OwningProcess=9999)]:
            with self.subTest(other=other),self.assertRaises(ValueError):
                validate_windows_listeners([good,other],PID,18080)
        with self.assertRaises(ValueError): validate_windows_listeners([dict(good,OwningProcess=None)],PID,18080)

    def test_unowned_unrelated_socket_is_not_an_app_listener(self):
        self.assertEqual(validate_listeners('LISTEN 0 100 0.0.0.0:22 *:*\n'+row(),PID),row())
        with self.assertRaises(ValueError): validate_listeners('LISTEN 0 100 127.0.0.1:18080 *:*',PID)

    def test_linux_process_stats_uses_full_inventory(self):
        with patch.object(check_local.os,'name','posix'), patch.object(check_local.Path,'read_text',return_value='VmRSS: 2 kB\nVmHWM: 3 kB'), patch.object(check_local.subprocess,'check_output',return_value=row('[::ffff:127.0.0.1]:18080')) as command:
            result=check_local.process_stats(PID,18080)
            self.assertEqual(result['addresses'],['127.0.0.1'])
            self.assertEqual(command.call_args.args[0],['ss','-H','-ltnp'])

    def test_windows_process_stats_uses_full_inventory(self):
        inventory=[{'LocalAddress':'127.0.0.1','LocalPort':18080,'OwningProcess':PID}]
        with patch.object(check_local.os,'name','nt'), patch.object(check_local.subprocess,'run',return_value=SimpleNamespace(stdout=json.dumps({'listeners':inventory}))) as command:
            self.assertEqual(check_local.process_stats(PID,18080)['addresses'],['127.0.0.1'])
            shell=command.call_args.args[0][-1]
            self.assertNotIn('-LocalPort',shell)
            self.assertNotIn('Where-Object',shell)

    def test_optimized_mode_is_rejected_before_help_or_execution(self):
        for name in ['check_local.py','operations.py','verify_https.py']:
            result=subprocess.run([sys.executable,'-B','-O',str(pathlib.Path(__file__).with_name(name)),'--help'],capture_output=True,text=True)
            self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
            self.assertIn('Python -O',result.stdout+result.stderr)

if __name__=='__main__': unittest.main(verbosity=2)
