#!/usr/bin/env python3
# Configuration checks. Optional nginx -t parses isolated files without starting a server.
import json
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import unittest
import package as tool


class DeploymentPackageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="pm-demo-render-test-")
        self.root = Path(self.temp.name)
        self.settings = json.loads((tool.HERE / "settings.example.json").read_text(encoding="utf-8"))
        self.config = self.root / "settings.json"
        self.env = self.root / "demo.env"
        self.write_settings()
        self.env.write_text("PM_DEMO_PORT=18421\nPM_DEMO_HEAP_MB=512\n", encoding="utf-8")

    def tearDown(self):
        self.temp.cleanup()

    def write_settings(self):
        self.config.write_text(json.dumps(self.settings), encoding="utf-8")

    def output(self, mode=False):
        return tool.render(self.config, self.env, self.root / ("bootstrap" if mode else "tls"), mode)

    def test_https_auth_covers_application_and_api_without_bypass(self):
        config = self.output()["project-manager-demo.conf"]
        tls = config.split("# BEGIN TLS", 1)[1]
        self.assertIn('auth_basic "Project Manager closed pilot";', tls)
        self.assertNotIn("auth_basic off", tls)
        self.assertEqual(tls.count("proxy_pass "), 1)
        self.assertIn("proxy_pass http://127.0.0.1:18421;", tls)
        self.assertIn('proxy_set_header Authorization "";', tls)
        self.assertIn("proxy_next_upstream off;", tls)
        self.assertIn("return 308 https://$host$request_uri;", config)

    def test_bootstrap_serves_acme_only_without_upstream_or_tls(self):
        config = self.output(True)["project-manager-demo.conf"]
        self.assertNotIn("proxy_pass", config)
        self.assertNotIn("listen 443", config)
        self.assertIn("location ^~ /.well-known/acme-challenge/", config)
        self.assertIn("return 404;", config)

    def test_all_four_modes_ship_complete_tools_and_correct_listens(self):
        for ipv6 in [False, True]:
            self.settings["ipv6"] = ipv6
            self.write_settings()
            for bootstrap in [False, True]:
                with self.subTest(ipv6=ipv6, bootstrap=bootstrap):
                    result = tool.render(self.config, self.env,
                                         self.root / f"matrix-{ipv6}-{bootstrap}", bootstrap)
                    config = result["project-manager-demo.conf"]
                    self.assertEqual("listen [::]:80;" in config, ipv6)
                    self.assertEqual("listen 443 ssl;" in config, not bootstrap)
                    self.assertEqual("listen [::]:443 ssl;" in config, ipv6 and not bootstrap)
                    self.assertNotIn("proxy_pass", result["bootstrap.conf"])
                    self.assertTrue(set(tool.SUPPORT_FILES).issubset(result))
                    self.assertIn("SHA256SUMS", result)

    def test_diagnostics_and_hidden_files_are_denied(self):
        tls = self.output()["project-manager-demo.conf"].split("# BEGIN TLS", 1)[1]
        for token in ["h2-console", "actuator", "error", "api/(?:health", "return 404;"]:
            self.assertIn(token, tls)
        self.assertIn(r"location ~ /\.", tls)

    def test_diagnostic_regex_is_one_quoted_nginx_argument(self):
        tls = self.output()["project-manager-demo.conf"].split("# BEGIN TLS", 1)[1]
        match = re.search(r'location ~\* "([^"]+)" \{\n\s+return 404;\n\s+\}', tls)
        self.assertIsNotNone(match, "Semicolon in the regex must not terminate location")
        pattern = re.compile(match[1], re.IGNORECASE)
        for path in ["/h2-console/", "/H2-CONSOLE;x", "/actuator", "/api/health",
                     "/api/health;x", "/v3/api-docs/", "/error"]:
            with self.subTest(path=path):
                self.assertIsNotNone(pattern.search(path))
        for path in ["/", "/app.js", "/api/projects", "/api/projects/123/dashboard"]:
            with self.subTest(path=path):
                self.assertIsNone(pattern.search(path))

    @unittest.skipUnless(shutil.which("nginx") and shutil.which("openssl"),
                         "nginx/openssl unavailable: syntax NOT VERIFIED here")
    def test_generated_configs_pass_nginx_parser_and_old_regex_fails(self):
        cert, key = self.root / "test.crt", self.root / "test.key"
        subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
                        "-days", "1", "-subj", "/CN=syntax-test.invalid",
                        "-keyout", str(key), "-out", str(cert)], check=True, capture_output=True)
        self.settings["tls_cert"], self.settings["tls_key"] = str(cert), str(key)
        candidate = self.root / "candidate.conf"
        wrapper = self.root / "nginx.conf"
        wrapper.write_text(f'error_log stderr;\npid {self.root}/nginx.pid;\n'
                           f'events {{}}\nhttp {{ access_log off; include {candidate}; }}\n',
                           encoding="utf-8")
        command = [shutil.which("nginx"), "-t", "-e", "stderr", "-p", str(self.root),
                   "-c", str(wrapper)]
        # nginx -t may open listen sockets. Use unprivileged loopback ports in this
        # parser test; the exact deployment file is checked separately as root.
        def free_port():
            with socket.socket() as listener:
                listener.bind(("127.0.0.1", 0))
                return listener.getsockname()[1]
        http_port, https_port = free_port(), free_port()
        for ipv6 in [False, True]:
            self.settings["ipv6"] = ipv6
            self.write_settings()
            for bootstrap in [False, True]:
                with self.subTest(ipv6=ipv6, bootstrap=bootstrap):
                    config = tool.render(self.config, self.env,
                                         self.root / f"syntax-{ipv6}-{bootstrap}", bootstrap)["project-manager-demo.conf"]
                    config = config.replace("listen 80;", f"listen 127.0.0.1:{http_port};")
                    config = config.replace("listen 443 ssl;", f"listen 127.0.0.1:{https_port} ssl;")
                    config = config.replace("listen [::]:80;", f"listen [::1]:{http_port};")
                    config = config.replace("listen [::]:443 ssl;", f"listen [::1]:{https_port} ssl;")
                    candidate.write_text(config, encoding="utf-8")
                    result = subprocess.run(command, capture_output=True, text=True)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    if not bootstrap:
                        broken = re.sub(r'location ~\* "([^"]+)"', r'location ~* \1', config)
                        self.assertNotEqual(broken, config)
                        candidate.write_text(broken, encoding="utf-8")
                        result = subprocess.run(command, capture_output=True, text=True)
                        self.assertNotEqual(result.returncode, 0)
                        self.assertIn('has no opening "{"', result.stderr)

    def test_unit_pins_isolation_and_never_launches_a_shell(self):
        unit = self.output()["project-manager-demo.service"]
        for arg in [
            "--spring.profiles.active=demo", "--spring.datasource.url=jdbc:h2:mem:pmdemo_vps",
            "--spring.h2.console.enabled=false", "--server.address=127.0.0.1",
            "--server.port=" + "$" + "{PM_DEMO_PORT}", "--spring.config.location=classpath:/",
            "User=pm-demo", "Group=pm-demo", "NoNewPrivileges=true", "ProtectSystem=strict",
        ]:
            self.assertIn(arg, unit)
        self.assertNotIn("/bin/sh", unit)
        self.assertNotIn("jdbc:h2:file", unit)
        self.assertNotIn("--spring.datasource.hikari", unit)

    @unittest.skipUnless(sys.platform == "linux" and shutil.which("systemd-analyze")
                         and shutil.which("java"), "Linux/systemd/Java unavailable: NOT VERIFIED")
    def test_systemd_unit_verifies_on_available_linux(self):
        self.settings["java_bin"] = str(Path(shutil.which("java")).resolve())
        self.write_settings()
        self.output()
        unit = self.root / "tls/project-manager-demo.service"
        result = subprocess.run(["systemd-analyze", "verify", str(unit)],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_env_has_two_shared_nonsecret_parameters(self):
        result = self.output()
        self.assertIn("PM_DEMO_PORT=18421\n", result["demo.env"])
        self.assertIn("-Xmx" + "$" + "{PM_DEMO_HEAP_MB}m", result["project-manager-demo.service"])
        self.assertIn(":18421;", result["project-manager-demo.conf"])

    def test_environment_rejects_override_and_duplicates(self):
        for value in [
            "PM_DEMO_PORT=18080\nPM_DEMO_HEAP_MB=512\nSPRING_PROFILES_ACTIVE=other\n",
            "PM_DEMO_PORT=18080\nPM_DEMO_PORT=19000\nPM_DEMO_HEAP_MB=512\n",
            "PM_DEMO_PORT=18080;bad\nPM_DEMO_HEAP_MB=512\n",
        ]:
            self.env.write_text(value, encoding="utf-8")
            with self.subTest(value=value), self.assertRaises(ValueError):
                tool.read_environment(self.env)

    def test_privileged_or_invalid_ports_and_excessive_heap_are_rejected(self):
        for port, heap in [(80, 512), (65536, 512), (18421, 1024), (18421, 1)]:
            self.env.write_text(f"PM_DEMO_PORT={port}\nPM_DEMO_HEAP_MB={heap}\n", encoding="utf-8")
            with self.subTest(port=port, heap=heap), self.assertRaises(ValueError):
                tool.read_environment(self.env)

    def test_domain_config_injection_is_rejected(self):
        for value in ["https://pm.example.com", "*.example.com", "pm.example.com;auth_basic off",
                      "pm.example.com\nserver", "example.com:443"]:
            self.settings["domain"] = value
            self.write_settings()
            with self.subTest(domain=value), self.assertRaises(ValueError):
                tool.read_settings(self.config)

    def test_unsafe_paths_and_root_identity_are_rejected(self):
        for key, value in [("app_root", "/"), ("app_root", "/opt/../etc"),
                           ("auth_file", "/etc/x;\n"), ("tls_key", "/etc/$secret"),
                           ("app_user", "root"), ("app_group", "root")]:
            original = self.settings[key]
            self.settings[key] = value
            self.write_settings()
            with self.subTest(key=key), self.assertRaises(ValueError):
                tool.read_settings(self.config)
            self.settings[key] = original

    def test_unknown_and_duplicate_settings_are_rejected(self):
        self.settings["extra_config"] = "unexpected"
        self.write_settings()
        with self.assertRaises(ValueError):
            tool.read_settings(self.config)
        self.config.write_text('{"domain":"one.example","domain":"two.example"}', encoding="utf-8")
        with self.assertRaises(ValueError):
            tool.read_settings(self.config)

    def test_ipv6_is_explicit_and_output_is_not_overwritten(self):
        self.settings["ipv6"] = True
        self.write_settings()
        result = self.output()
        self.assertIn("listen [::]:443 ssl;", result["project-manager-demo.conf"])
        with self.assertRaises(ValueError):
            self.output()

    def test_build_requires_full_revision_and_expected_default(self):
        self.assertEqual(tool.BASE_SHA, "900113b54e14658ee7a0fc3ccae13e877ad78192")
        for value in ["HEAD", "codex-dev", "900113b", "a" * 40 + ";bad"]:
            with self.subTest(revision=value), self.assertRaises(ValueError):
                tool.build(".", self.root / "release", "mvn", value)


if __name__ == "__main__":
    unittest.main(verbosity=2)
