#!/usr/bin/env python3
"""Execute the actual rollback functions with controlled command failures; no real services."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest

def pathlib_work(work):
 return Path(work).as_posix()

SCRIPT = Path(__file__).with_name('https-transaction.sh')
FUNCTIONS = SCRIPT.read_text().split('# BEGIN ROLLBACK FUNCTIONS\n', 1)[1].split('# END ROLLBACK FUNCTIONS', 1)[0]
MOCKS = r'''
set -Eeuo pipefail
nginx_service=nginx
domain=demo.example.com
scenario=$1
work=$2
acme_backup="$work/acme.conf"
live_config="$work/live.conf"
printf ACME > "$acme_backup"
printf HTTPS > "$live_config"
record() { echo "$*" >> "$work/calls"; }
cp() {
 record cp
 [[ $scenario != copy_fail ]] || return 1
 printf ACME > "$live_config"
}
cmp() { [[ $(<"$acme_backup") == $(<"$live_config") ]]; }
nginx() { record nginx-test; [[ $scenario != syntax_fail ]]; }
systemctl() {
 record "systemctl $*"
 case "$1" in
 reload)
  [[ $scenario != reload_fail ]] || return 1
  : > "$work/reloaded" ;;
 stop)
  [[ $scenario != stop_fail ]] || return 1
  : > "$work/stopped" ;;
 mask)
  [[ $scenario != mask_fail ]] || return 1
  : > "$work/masked" ;;
 is-active)
  if [[ -f "$work/stopped" ]]; then
   if [[ ${2:-} != --quiet ]]; then echo inactive; fi
   return 3
  fi
  if [[ ${2:-} != --quiet ]]; then echo active; fi
  return 0 ;;
 is-enabled)
  if [[ -f "$work/masked" ]]; then echo masked; return 1; fi
  echo enabled ;;
 *) return 99 ;;
 esac
}
ss() {
 record "ss $*"
 [[ $scenario != ss_fail ]] || return 1
 if [[ -f "$work/stopped" && $scenario != surviving_listener ]]; then return 0; fi
 if [[ "$*" == *:443* ]]; then
  if [[ -f "$work/reloaded" && $scenario == success ]]; then return 0; fi
  if [[ -f "$work/reloaded" && $scenario == http_bad ]]; then return 0; fi
  echo 'LISTEN 0 511 [::]:443 [::]:*'
 else
  echo 'LISTEN 0 511 0.0.0.0:80 0.0.0.0:*'
 fi
}
curl() {
 record curl
 if [[ "$*" == *pm-demo-publish-probe* ]]; then echo PM_DEMO_ACME_ONLY
 elif [[ $scenario == http_bad ]]; then echo 200
 else echo 404
 fi
}
sleep() { :; }
'''

class RollbackTest(unittest.TestCase):
 def run_case(self, scenario, closed, stopped, trigger_trap=False):
  with tempfile.TemporaryDirectory() as work:
   ending = '\ntrap rollback ERR\nfalse\n' if trigger_trap else '\nrollback\n'
   result=subprocess.run([os.environ.get('PM_DEMO_TEST_SHELL','bash'),'-c',MOCKS+'\n'+FUNCTIONS+ending,'test',scenario,pathlib_work(work)],text=True,capture_output=True)
   output=result.stdout+result.stderr
   calls=Path(work,'calls').read_text()
   self.assertEqual('PM_DEMO_HTTPS_FAILED_ROUTE_CLOSED' in output,closed,output)
   self.assertEqual('PM_DEMO_HTTPS_FAILED_ROUTE_CLOSURE_NOT_CONFIRMED' in output,not closed,output)
   self.assertEqual('systemctl stop nginx' in calls,stopped,calls)
   self.assertEqual('systemctl mask nginx' in calls,stopped,calls)
   self.assertEqual(result.returncode != 0,trigger_trap or not closed,output)
   self.assertIn('ss -H -ltn ( sport = :443 )',calls)
   if scenario=='success':
    self.assertIn('PM_DEMO_ACME_RESTORED_VERIFIED',output)
    self.assertNotIn('PM_DEMO_NGINX_STOPPED_MASKED_VERIFIED',output)
   if scenario=='syntax_fail': self.assertNotIn('systemctl reload nginx',calls)
   return calls
 def test_successful_acme_restore(self): self.run_case('success',True,False)
 def test_syntax_failure_stops_and_masks(self): self.run_case('syntax_fail',True,True)
 def test_reload_failure_stops_and_masks(self): self.run_case('reload_fail',True,True)
 def test_lingering_443_stops_and_masks(self): self.run_case('lingering',True,True)
 def test_http_not_acme_stops_and_masks(self): self.run_case('http_bad',True,True)
 def test_copy_failure_stops_and_masks(self): self.run_case('copy_fail',True,True)
 def test_stop_failure_does_not_claim_closed(self): self.run_case('stop_fail',False,True)
 def test_mask_failure_does_not_claim_closed(self): self.run_case('mask_fail',False,True)
 def test_ss_failure_does_not_claim_closed(self): self.run_case('ss_fail',False,True)
 def test_remaining_listener_does_not_claim_closed(self): self.run_case('surviving_listener',False,True)
 def test_err_trap_preserves_failure_after_safe_restore(self): self.run_case('success',True,False,True)
 def test_err_trap_handles_failed_reload(self): self.run_case('reload_fail',True,True,True)

if __name__=='__main__': unittest.main(verbosity=2)