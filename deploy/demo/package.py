#!/usr/bin/env python3
# Standard-library deployment preparation. This tool never installs services or contacts a VPS.
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import tempfile
import zipfile

BASE_SHA = "900113b54e14658ee7a0fc3ccae13e877ad78192"
HERE = Path(__file__).resolve().parent
FIELDS = {"domain", "app_root", "app_user", "app_group", "java_bin", "env_file",
          "auth_file", "acme_webroot", "tls_cert", "tls_key", "ipv6"}


def read_settings(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("Duplicate setting: " + key)
            result[key] = value
        return result
    data = json.loads(Path(path).read_text(encoding="utf-8-sig"), object_pairs_hook=unique)
    if set(data) != FIELDS:
        raise ValueError("Settings must contain exactly the documented non-secret keys")
    domain = data["domain"]
    if not isinstance(domain, str) or len(domain) > 253 or not re.fullmatch(
        r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+", domain
    ):
        raise ValueError("Use one lowercase ASCII DNS hostname, without scheme, wildcard or port")
    for key in ("app_user", "app_group"):
        value = data[key]
        if not isinstance(value, str) or value == "root" or not re.fullmatch(r"[a-z_][a-z0-9_-]{0,30}", value):
            raise ValueError("A dedicated non-root identity is required: " + key)
    if type(data["ipv6"]) is not bool:
        raise ValueError("ipv6 must be boolean")
    for key in FIELDS - {"domain", "app_user", "app_group", "ipv6"}:
        value = data[key]
        if not isinstance(value, str) or not re.fullmatch(r"/[A-Za-z0-9_./+-]+", value):
            raise ValueError("Use an absolute Unix path without spaces or config metacharacters: " + key)
        if value == "/" or ".." in PurePosixPath(value).parts:
            raise ValueError("Unsafe path: " + key)
        data[key] = str(PurePosixPath(value))
    return data


def read_environment(path):
    values = {}
    for line in Path(path).read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        match = re.fullmatch(r"(PM_DEMO_PORT|PM_DEMO_HEAP_MB)=([0-9]+)", line)
        if not match or match[1] in values:
            raise ValueError("Environment accepts only two unique integer assignments")
        values[match[1]] = int(match[2])
    if set(values) != {"PM_DEMO_PORT", "PM_DEMO_HEAP_MB"}:
        raise ValueError("Both PM_DEMO_PORT and PM_DEMO_HEAP_MB are required")
    if not 1024 <= values["PM_DEMO_PORT"] <= 65535:
        raise ValueError("Java must use an unprivileged TCP port")
    if not 128 <= values["PM_DEMO_HEAP_MB"] <= 512:
        raise ValueError("Pilot heap must be 128..512 MiB (service MemoryMax=1G)")
    return values


def render(settings_path, env_path, output, bootstrap=False):
    settings, env = read_settings(settings_path), read_environment(env_path)
    replacements = {key.upper(): str(value) for key, value in settings.items()}
    replacements.update(
        PORT=str(env["PM_DEMO_PORT"]),
        UNIT_PORT="$" + "{PM_DEMO_PORT}",
        UNIT_HEAP="$" + "{PM_DEMO_HEAP_MB}",
        HTTP_IPV6="listen [::]:80;" if settings["ipv6"] else "",
        HTTPS_IPV6="listen [::]:443 ssl;" if settings["ipv6"] else "",
        HTTP_ACTION="return 404;" if bootstrap else "return 308 https://$host$request_uri;",
    )
    rendered = {}
    for template, filename in [
        ("project-manager-demo.service.in", "project-manager-demo.service"),
        ("nginx.conf.in", "project-manager-demo.conf"),
    ]:
        value = (HERE / template).read_text(encoding="utf-8")
        if bootstrap and template == "nginx.conf.in":
            value = re.sub(r"# BEGIN TLS\n.*?# END TLS\n?", "", value, flags=re.S)
        for key, replacement in replacements.items():
            value = value.replace("__" + key + "__", replacement)
        if re.search(r"__[A-Z_]+__", value):
            raise ValueError("Unresolved template placeholder")
        rendered[filename] = value
    rendered["demo.env"] = "".join(key + "=" + str(env[key]) + "\n" for key in sorted(env))
    rendered["render.json"] = json.dumps(
        {"mode": "bootstrap-acme-only" if bootstrap else "https-basic-auth",
         "settings": settings, "environment": env}, ensure_ascii=False, indent=2
    ) + "\n"
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    if any((output / name).exists() for name in rendered):
        raise ValueError("Output files already exist; render into a new directory")
    for name, value in rendered.items():
        (output / name).write_text(value, encoding="utf-8", newline="\n")
    return rendered


def build(repo, output, maven, revision=BASE_SHA):
    if not re.fullmatch(r"[0-9a-f]{40}", revision):
        raise ValueError("Use a full reviewed commit SHA, never a branch or abbreviated ref")
    repo, output = Path(repo).resolve(), Path(output).resolve()
    def git(*args):
        return subprocess.check_output(["git", "-C", str(repo), *args], text=True).strip()
    if git("cat-file", "-t", revision) != "commit":
        raise ValueError("Revision is not a commit")
    timestamp = git("show", "-s", "--format=%ct", revision)
    if output.exists():
        raise ValueError("Release output must not exist; previous JARs are never overwritten")
    output.mkdir(parents=True)
    java_version = subprocess.check_output(["java", "-version"], stderr=subprocess.STDOUT, text=True)
    if not re.search(r'version "21[."]', java_version):
        raise ValueError("Build requires JDK 21")
    maven_version = subprocess.check_output([maven, "-v"], stderr=subprocess.STDOUT, text=True)
    with tempfile.TemporaryDirectory(prefix="pm-demo-build-") as temp:
        stage = Path(temp).resolve()
        archive = stage / "source.zip"
        subprocess.run(["git", "-C", str(repo), "archive", "--format=zip",
                        "--output=" + str(archive), revision, "pom.xml", "src"], check=True)
        source = stage / "source"
        source.mkdir()
        with zipfile.ZipFile(archive) as zipped:
            for info in zipped.infolist():
                destination = (source / info.filename).resolve()
                if not destination.is_relative_to(source) or stat.S_ISLNK(info.external_attr >> 16):
                    raise ValueError("Unsafe source archive entry")
            zipped.extractall(source)
        command = [maven, "-B", "-ntp", "-DskipTests",
                   "-Dproject.build.outputTimestamp=" + timestamp, "package"]
        with (output / "build.log").open("w", encoding="utf-8") as log:
            subprocess.run(command, cwd=source, stdout=log, stderr=subprocess.STDOUT, check=True)
        jars = list((source / "target").glob("*.jar"))
        if len(jars) != 1:
            raise ValueError("Expected one application JAR")
        shutil.copyfile(jars[0], output / "project-manager.jar")
    jar = output / "project-manager.jar"
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    with zipfile.ZipFile(jar) as zipped:
        guard_path = next((name for name in ("META-INF/spring.factories",
                           "BOOT-INF/classes/META-INF/spring.factories") if name in zipped.namelist()), None)
        if guard_path is None:
            raise ValueError("DataSource guard registration is missing")
        guard = zipped.read(guard_path).decode()
        if "project_manager.safety.IsolatedDatabaseGuard" not in guard:
            raise ValueError("DataSource guard is missing")
        for required in ["BOOT-INF/classes/application-demo.properties",
                         "BOOT-INF/classes/project_manager/safety/IsolatedDatabaseGuard.class"]:
            zipped.getinfo(required)
    release = {"gitSha": revision, "sourceDateEpoch": int(timestamp), "jarSha256": digest,
               "java": java_version.splitlines()[0], "maven": maven_version.splitlines()[0],
               "buildCommand": command, "sourcePaths": ["pom.xml", "src"]}
    (output / "release.json").write_text(json.dumps(release, indent=2) + "\n", encoding="utf-8")
    (output / "SHA256SUMS").write_text(digest + "  project-manager.jar\n", encoding="utf-8")
    return release


def main():
    parser = argparse.ArgumentParser(description="Prepare an isolated Project Manager demo package; no remote actions")
    commands = parser.add_subparsers(dest="command", required=True)
    b = commands.add_parser("build")
    b.add_argument("--repo", default=".")
    b.add_argument("--out", required=True)
    b.add_argument("--maven", default="mvn")
    b.add_argument("--revision", default=BASE_SHA)
    r = commands.add_parser("render")
    r.add_argument("--settings", required=True)
    r.add_argument("--env", required=True)
    r.add_argument("--out", required=True)
    r.add_argument("--bootstrap", action="store_true")
    args = parser.parse_args()
    if args.command == "build":
        print(json.dumps(build(args.repo, args.out, args.maven, args.revision), indent=2))
    else:
        render(args.settings, args.env, args.out, args.bootstrap)
        print("Prepared files in " + args.out + "; nothing installed")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, subprocess.SubprocessError, OSError) as error:
        raise SystemExit("Preparation failed: " + str(error))
