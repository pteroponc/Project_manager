#!/usr/bin/env python3
# Local verification of the rendered ExecStart, NOT an alternative server deployment.
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import socket
import subprocess
import time
import urllib.error
import urllib.request
import zipfile
import package as tool


def request(base, path, method="GET", data=None):
    body = None if data is None else json.dumps(data).encode()
    headers = {"Accept": "application/json" if path.startswith("/api/") else "*/*"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(base + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=3) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()


def process_stats(pid, port):
    if os.name == "nt":
        script = (
            f"$jproc=Get-Process -Id {pid};"
            f"$bound=@(Get-NetTCPConnection -LocalPort {port} -State Listen "
            f"-ErrorAction Stop | Where-Object OwningProcess -eq {pid} "
            "| Select-Object -ExpandProperty LocalAddress);"
            "@{rssBytes=$jproc.WorkingSet64;peakRssBytes=$jproc.PeakWorkingSet64;"
            "cpuSeconds=$jproc.CPU;threads=$jproc.Threads.Count;addresses=$bound}"
            "|ConvertTo-Json -Compress"
        )
        result = subprocess.run(["powershell.exe", "-NoProfile", "-Command", script],
                                capture_output=True, text=True, check=True)
        return json.loads(result.stdout)
    values = {}
    for line in Path(f"/proc/{pid}/status").read_text().splitlines():
        if line.startswith(("VmRSS:", "VmHWM:")):
            key, amount, _unit = line.split()
            values["rssBytes" if key == "VmRSS:" else "peakRssBytes"] = int(amount) * 1024
    result = subprocess.check_output(["ss", "-H", "-ltnp", "sport = :" + str(port)], text=True)
    values["addresses"] = [line.split()[3].rsplit(":", 1)[0] for line in result.splitlines()]
    return values


def stop(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)


def check(unit, env_file, jar, java, output):
    env = tool.read_environment(env_file)
    port = env["PM_DEMO_PORT"]
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", port))  # Fail if occupied; never stop another process.
    jar = Path(jar).resolve()
    output = Path(output).resolve()
    output.mkdir(parents=True, exist_ok=False)
    runtime = output / "runtime"
    runtime.mkdir()
    line = next(line for line in Path(unit).read_text(encoding="utf-8").splitlines()
                if line.startswith("ExecStart="))
    command = line.removeprefix("ExecStart=")
    for key, value in env.items():
        command = command.replace("$" + "{" + key + "}", str(value))
    argv = shlex.split(command)
    critical = {
        "--spring.profiles.active": "demo",
        "--spring.datasource.url": "jdbc:h2:mem:pmdemo_vps",
        "--spring.h2.console.enabled": "false",
        "--server.address": "127.0.0.1",
        "--server.port": str(port),
        "--spring.config.location": "classpath:/",
    }
    for key, value in critical.items():
        matching = [arg for arg in argv if arg.startswith(key + "=")]
        if matching != [key + "=" + value]:
            raise ValueError("Unsafe or duplicate rendered runtime flag: " + key)
    # Only platform-specific executable/artifact/scratch paths are substituted.
    argv[0] = str(Path(java).resolve())
    argv[argv.index("-jar") + 1] = str(jar)
    argv = ["-Djava.io.tmpdir=" + str(runtime) if a.startswith("-Djava.io.tmpdir=") else a for a in argv]
    base = "http://127.0.0.1:" + str(port)
    inherited = os.environ.copy()
    for key in ["JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"]:
        inherited.pop(key, None)
    results = {
        "url": base, "argv": argv, "unit": str(Path(unit).resolve()),
        "jarSha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "notVerified": ["systemd manager/sandbox/cgroups/UID on Linux", "nginx -t and proxy",
                        "HTTPS, Basic Auth and public connectivity", "TLS issuance/renewal"],
        "runs": [],
    }
    with zipfile.ZipFile(jar) as zipped:
        expected_js = hashlib.sha256(zipped.read("BOOT-INF/classes/public/app.js")).hexdigest()
    creationflags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0

    def start(name, extras=None):
        log = (output / (name + ".log")).open("w", encoding="utf-8")
        child_env = inherited.copy()
        child_env.update(extras or {})
        process = subprocess.Popen(argv, cwd=runtime, env=child_env, stdout=log,
                                   stderr=subprocess.STDOUT, creationflags=creationflags)
        return process, log

    def ready(process):
        deadline = time.monotonic() + 75
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise ValueError("Java exited before readiness")
            try:
                status, content = request(base, "/api/projects")
                rows = json.loads(content)
                if status == 200 and isinstance(rows, list) and len(rows) == 3:
                    return rows
            except (OSError, ValueError):
                pass
            time.sleep(0.25)
        raise TimeoutError("No healthy seeded demo within 75s")

    original_ids = set()
    for number in [1, 2]:
        started = time.monotonic()
        process, log = start("run-" + str(number))
        try:
            projects = ready(process)
            run = {"pid": process.pid, "readySeconds": round(time.monotonic() - started, 3), "http": {}}
            for path, expected in [("/", 200), ("/api/health", 200), ("/api/projects", 200),
                                   ("/h2-console", 404), ("/h2-console/", 404),
                                   ("/h2-console/login.do", 404), ("/actuator/health", 404)]:
                status, content = request(base, path)
                if status != expected:
                    raise ValueError(f"{path}: expected {expected}, got {status}")
                run["http"][path] = status
                if path == "/" and b"/app.js?" not in content:
                    raise ValueError("Application HTML is missing")
            status, script = request(base, "/app.js")
            if status != 200 or hashlib.sha256(script).hexdigest() != expected_js:
                raise ValueError("Served JavaScript differs from the JAR")
            run["appJsMatchesJar"] = True
            project_id = projects[0]["id"]
            status, dashboard = request(base, "/api/projects/" + project_id + "/dashboard")
            if status != 200 or json.loads(dashboard)["project"]["id"] != project_id:
                raise ValueError("Dashboard API unavailable")
            run["dashboardStatus"] = status
            run["process"] = process_stats(process.pid, port)
            if run["process"]["addresses"] != ["127.0.0.1"]:
                raise ValueError("Unexpected listener address")
            if number == 1:
                original_ids = {p["id"] for p in projects}
                status, card = request(base, "/api/projects/" + project_id + "/card")
                baseline = json.loads(card)
                status, updated = request(base, "/api/projects/" + project_id, "PATCH",
                                          {"expectedVersion": baseline["version"], "owner": "DEPLOY_RESET_PROBE"})
                if status != 200 or json.loads(updated)["owner"] != "DEPLOY_RESET_PROBE":
                    raise ValueError("Reset probe was not stored")
                run["resetProbeWritten"] = True
            else:
                if original_ids.intersection(p["id"] for p in projects):
                    raise ValueError("Old IDs survived the JVM restart")
                if any(p["owner"] == "DEPLOY_RESET_PROBE" for p in projects):
                    raise ValueError("Demo change survived the restart")
                run["restartResetConfirmed"] = True
            results["runs"].append(run)
            print("Local run", number, "PASS", flush=True)
        finally:
            stop(process)
            log.close()
    # Even the rejected override names only a separate memory DB; no disk/working DB is touched.
    process, log = start("guard-rejection", {
        "SPRING_DATASOURCE_HIKARI_JDBC_URL": "jdbc:h2:mem:pmdemo_rejected_override"
    })
    try:
        try:
            exit_code = process.wait(timeout=30)
        except subprocess.TimeoutExpired:
            raise ValueError("DataSource guard failed to reject the override")
    finally:
        stop(process)
        log.close()
    rejected = (output / "guard-rejection.log").read_text(encoding="utf-8", errors="replace")
    if exit_code == 0 or not re.search(r"Unsafe (datasource override|connection properties)", rejected):
        raise ValueError("No expected datasource guard rejection")
    if "HikariPool-1 - Starting" in rejected:
        raise ValueError("DataSource initialized before rejection")
    results["datasourceGuardRejectsOverrideBeforeConnection"] = True
    results["ownProcessesStopped"] = True
    (output / "local-results.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
    print("PASS: two sequential isolated runs, restart reset, loopback and DataSource guard", flush=True)
    return results


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Verify a rendered demo locally; no VPS/proxy actions")
    parser.add_argument("--unit", required=True)
    parser.add_argument("--env", required=True)
    parser.add_argument("--jar", required=True)
    parser.add_argument("--java", default=shutil.which("java"))
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    if not args.java:
        raise SystemExit("Java 21 is required")
    check(args.unit, args.env, args.jar, args.java, args.out)
