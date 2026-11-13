#!/bin/bash
# Internal transaction, invoked only by operations.py after validated preflight.
set -Eeuo pipefail
export PATH=/usr/sbin:/usr/bin:/sbin:/bin
umask 077
[[ $# == 11 && $EUID == 0 ]] || { echo 'Use operations.py publish as root'; exit 64; }
https_file=$1
live_config=$2
acme_backup=$3
domain=$4
nginx_service=$5
nginx_bin=$6
systemctl_bin=$7
ss_bin=$8
curl_bin=$9
python_bin=${10}
smoke_file=${11}
nginx() { "$nginx_bin" "$@"; }
systemctl() { "$systemctl_bin" "$@"; }
ss() { "$ss_bin" "$@"; }
curl() { "$curl_bin" "$@"; }
# BEGIN ROLLBACK FUNCTIONS
listeners_absent() {
 local sockets
 sockets=$(ss -H -ltn "( sport = :$1 )") || return 1
 [[ -z $sockets ]]
}
verify_acme_only() {
 local code path
 cmp -s "$acme_backup" "$live_config" || return 1
 systemctl is-active --quiet "$nginx_service" || return 1
 listeners_absent 443 || return 1
 for path in / /app.js /api/projects; do
  code=$(curl --noproxy '*' --max-time 2 -sS -H "Host: $domain" -o /dev/null -w '%{http_code}' "http://127.0.0.1$path") || return 1
  [[ $code == 404 ]] || return 1
 done
 code=$(curl --noproxy '*' --max-time 2 -sS -H "Host: $domain" http://127.0.0.1/.well-known/acme-challenge/pm-demo-publish-probe) || return 1
 [[ $code == PM_DEMO_ACME_ONLY ]]
}
wait_acme_only() {
 local attempt
 for attempt in {1..5}; do
  if verify_acme_only; then return 0; fi
  sleep 1
 done
 return 1
}
verify_stopped_and_masked() {
 local state mask_state
 state=$(systemctl is-active "$nginx_service" 2>/dev/null) || :
 [[ $state == inactive || $state == failed ]] || return 1
 mask_state=$(systemctl is-enabled "$nginx_service" 2>/dev/null) || :
 [[ $mask_state == masked ]] || return 1
 listeners_absent 443 || return 1
 listeners_absent 80 || return 1
}
rollback() {
 trap - ERR
 local attempt
 if cp "$acme_backup" "$live_config" && nginx -t && systemctl reload "$nginx_service" && wait_acme_only; then
  echo PM_DEMO_ACME_RESTORED_VERIFIED
  echo PM_DEMO_HTTPS_FAILED_ROUTE_CLOSED
  return 0
 fi
 echo 'ACME restoration not confirmed; stopping and masking Nginx.' >&2
 if ! systemctl stop "$nginx_service"; then echo 'Nginx stop returned an error.' >&2; fi
 if ! systemctl mask "$nginx_service"; then echo 'Nginx mask returned an error.' >&2; fi
 for attempt in {1..5}; do
  if verify_stopped_and_masked; then
   echo PM_DEMO_NGINX_STOPPED_MASKED_VERIFIED
   echo PM_DEMO_HTTPS_FAILED_ROUTE_CLOSED
   return 0
  fi
  sleep 1
 done
 echo PM_DEMO_HTTPS_FAILED_ROUTE_CLOSURE_NOT_CONFIRMED >&2
 return 1
}
# END ROLLBACK FUNCTIONS
trap rollback ERR
cp "$https_file" "$live_config"
nginx -t
systemctl reload "$nginx_service"
ready=no
for attempt in {1..10}; do
 code=$(curl --max-time 3 --resolve "$domain:443:127.0.0.1" -sS -o /dev/null -w '%{http_code}' "https://$domain/" || true)
 if [[ $code == 401 ]]; then ready=yes; break; fi
 sleep 1
done
[[ $ready == yes ]]
"$python_bin" "$smoke_file" --domain "$domain"
trap - ERR
echo PM_DEMO_HTTPS_COMPLETE
