#!/usr/bin/env python3
"""Post-activation checks against loopback; verifies the public hostname's TLS chain."""
import argparse
import base64
import http.client
import json
import socket
import ssl

class LoopbackTLS(http.client.HTTPSConnection):
    def connect(self):
        raw = socket.create_connection(('127.0.0.1', 443), self.timeout)
        self.sock = self._context.wrap_socket(raw, server_hostname=self.host)


def verify(domain):
    # Same conservative hostname policy as the renderer, without evaluating shell text.
    import re
    if not re.fullmatch(r'[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?', domain) or '.' not in domain:
        raise ValueError('Invalid domain')
    tls = LoopbackTLS(domain, timeout=10, context=ssl.create_default_context())
    plain = http.client.HTTPConnection('127.0.0.1', 80, timeout=10)
    results = []
    try:
        cases = [(path, 'GET', 401, bad) for path in ['/', '/app.js', '/api/projects'] for bad in [False, True]]
        cases += [('/api/projects' if method == 'POST' else '/api/projects/00000000-0000-0000-0000-000000000001', method, 401, False) for method in ['POST', 'PATCH', 'PUT', 'DELETE']]
        cases += [(path, 'GET', 404, False) for path in ['/h2-console/', '/actuator/', '/api/health']]
        for path, method, expected, bad in cases:
            headers = {'Host': domain}
            if bad:
                headers['Authorization'] = 'Basic ' + base64.b64encode(b'nonexistent-probe-user:invalid-probe').decode()
            tls.request(method, path, headers=headers, body=b'{}' if method in ['POST', 'PUT', 'PATCH'] else None)
            response = tls.getresponse()
            response.read()
            if response.status != expected or (expected == 401 and not response.getheader('WWW-Authenticate', '').startswith('Basic ')):
                raise ValueError(f'Protection failed: {method} {path} returned {response.status}')
            results.append({'method': method, 'path': path, 'status': response.status, 'invalidProbe': bad})
        for path in ['/', '/api/projects']:
            plain.request('GET', path, headers={'Host': domain})
            response = plain.getresponse()
            response.read()
            if response.status != 308 or response.getheader('Location') != 'https://' + domain + path:
                raise ValueError('HTTP redirect failed')
        print(json.dumps({'result': 'PASS', 'https': results, 'redirect': 'PASS'}))
    finally:
        tls.close()
        plain.close()

if __name__ == '__main__':
    if not __debug__:
        raise SystemExit('Safety checks must not run under Python -O')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--domain', required=True)
    try:
        verify(parser.parse_args().domain)
    except (ValueError, OSError, http.client.HTTPException) as error:
        raise SystemExit('FAIL: ' + str(error))