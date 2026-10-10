import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
HISTORY = ROOT / "compatibility-bundles.json"
ASM_ARTIFACTS = {
    "asm": "876eab6a83daecad5ca67eb9fcabb063c97b5aeb8cf1fca7a989ecde17522051",
    "asm-commons": "3301a1c1cb4c59fcc5292648dac1d7c5aed4c0f067dfbe88873b8cdfe77404f4",
}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fetch(url: str, path: Path, sha256: str) -> None:
    with urllib.request.urlopen(url, timeout=60) as response:
        path.write_bytes(response.read())
    if digest(path) != sha256:
        raise ValueError(f"Checksum mismatch for {url}")


def run(command: list[str]) -> str:
    result = subprocess.run(command, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout


def toolchain(directory: Path, cli: Path) -> tuple[list[str], list[str]]:
    jars = []
    for artifact, checksum in ASM_ARTIFACTS.items():
        path = directory / f"{artifact}.jar"
        fetch(
            f"https://repo.maven.apache.org/maven2/org/ow2/asm/{artifact}/9.8/{artifact}-9.8.jar",
            path, checksum,
        )
        jars.append(str(path))
    classes = directory / "tools"
    classes.mkdir()
    run([
        "java", "com.sun.tools.javac.Main", "-cp", os.pathsep.join([str(cli), *jars]),
        "-d", str(classes), *map(str, (ROOT / "compatibility").glob("*.java")),
    ])
    return (
        ["java", "-cp", os.pathsep.join([str(classes), str(cli)]), "VerifyBundle"],
        ["java", "-cp", os.pathsep.join([str(classes), *jars]), "RelocateBundle"],
    )


def targets(checker: list[str], bundle: Path, required: set[str] | None = None) -> set[str]:
    output = run([*checker, str(bundle), *sorted(required or ())])
    print(output, end="")
    return set(re.findall(r"^([^\s:]+): \d+$", output, re.MULTILINE))


def assemble_compatible_bundle(
    current: Path, output: Path, version: str, cli: Path,
) -> set[str]:
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    d8s = list((sdk / "build-tools").glob("*/d8"))
    if not d8s:
        raise FileNotFoundError("Android SDK d8 is unavailable")
    d8 = max(d8s, key=lambda p: tuple(int(v) for v in re.findall(r"\d+", p.parent.name)))
    android_jars = list((sdk / "platforms").glob("*/android.jar"))
    android_jar = max(android_jars, key=lambda p: tuple(int(v) for v in re.findall(r"\d+", p.parent.name)))
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="piko-compatible-") as temporary:
        directory = Path(temporary)
        checker, relocator = toolchain(directory, cli.resolve())
        covered = targets(checker, current)
        inputs = [current]
        current_digest = digest(current)
        for entry in reversed(json.loads(HISTORY.read_text())):
            if entry["sha256"] == current_digest:
                continue
            bundle = directory / f"{entry['sha256']}.mpp"
            fetch(entry["download_url"], bundle, entry["sha256"])
            supported = targets(checker, bundle)
            if supported <= covered:
                continue
            relocated = directory / f"{entry['sha256']}.jar"
            run([
                *relocator, str(bundle), str(relocated), "h" + entry["sha256"][:12],
                *sorted(supported & covered),
            ])
            inputs.append(relocated)
            covered |= supported
        combined = directory / "combined.jar"
        written = set()
        with zipfile.ZipFile(combined, "w", zipfile.ZIP_DEFLATED) as destination:
            for bundle in inputs:
                with zipfile.ZipFile(bundle) as source:
                    for name in source.namelist():
                        if name.endswith("/") or name.endswith(".dex"):
                            continue
                        if name in written:
                            raise ValueError(f"Duplicate bundle entry {name}")
                        data = source.read(name)
                        if name == "META-INF/MANIFEST.MF":
                            text = data.decode()
                            text = re.sub(r"(?m)^Version: [^\r\n]+", "Version: " + version, text)
                            data = text.encode()
                        destination.writestr(name, data)
                        written.add(name)
        targets(checker, combined, covered)
        dex = directory / "dex"
        dex.mkdir()
        process = subprocess.run([
            str(d8), "--release", "--min-api", "26", "--lib", str(android_jar),
            "--classpath", str(cli.resolve()), "--output", str(dex), str(combined),
        ], text=True, capture_output=True)
        if process.returncode:
            raise RuntimeError(process.stdout + process.stderr)
        dex_files = sorted(dex.glob("classes*.dex"))
        if not dex_files:
            raise ValueError("d8 did not produce any classes.dex")
        with zipfile.ZipFile(combined) as source, zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as destination:
            for name in source.namelist():
                destination.writestr(name, source.read(name))
            for path in dex_files:
                destination.write(path, path.name)
        targets(checker, output, covered)
    return covered


def retain_bundle(path: Path, release_tag: str, repo: str, commit: str) -> Path:
    entries = json.loads(HISTORY.read_text())
    checksum = digest(path)
    # The visible release follows upstream, so a fork fix can replace assets on the
    # same tag. Keep history on content-addressed assets instead of mutable names.
    snapshot = path.with_name(f"{path.stem}-{checksum}{path.suffix}")
    shutil.copy2(path, snapshot)
    url = f"https://github.com/{repo}/releases/download/{release_tag}/{snapshot.name}"
    if any(entry["download_url"] == url for entry in entries):
        return snapshot
    entries.append({
        "version": release_tag,
        "download_url": url,
        "sha256": checksum,
        "piko_commit": commit,
    })
    HISTORY.write_text(json.dumps(entries, indent=2) + "\n")
    return snapshot
