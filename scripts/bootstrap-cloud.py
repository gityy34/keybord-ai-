#!/usr/bin/env python3
"""Install a verified, pinned Linux x86_64 build toolchain outside the checkout."""
import hashlib
import os
from pathlib import Path
import platform
import subprocess
import tarfile
from urllib.parse import urlparse
import zipfile

ROOT = Path(os.environ.get("RAABTA_TOOLCHAINS", "/workspace/toolchains"))
DOWNLOADS = ROOT / "downloads"


def download(url, name, checksum, algorithm="sha1"):
    archive = DOWNLOADS / name
    if not archive.exists() or hashlib.new(algorithm, archive.read_bytes()).hexdigest() != checksum:
        subprocess.run(["curl", "-fsSL", "--retry", "2", "--max-time", "180", url, "-o", str(archive)], check=True)
    if hashlib.new(algorithm, archive.read_bytes()).hexdigest() != checksum:
        raise RuntimeError(f"Checksum mismatch: {name}. Artifact will not be used.")
    return archive


def install_zip(archive, destination):
    with zipfile.ZipFile(archive) as bundle:
        prefix = bundle.namelist()[0].split("/")[0] + "/"
        for member in bundle.infolist():
            relative = member.filename.removeprefix(prefix)
            if not relative:
                continue
            target = destination / relative
            if not target.resolve().is_relative_to(destination.resolve()):
                raise RuntimeError("Unsafe archive path")
            if member.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(bundle.read(member))
                mode = member.external_attr >> 16
                if mode:
                    target.chmod(mode)


def main():
    if platform.system() != "Linux" or platform.machine() != "x86_64":
        raise RuntimeError("This optional cloud bootstrap targets Linux x86_64 only.")
    for folder in (DOWNLOADS, ROOT / "android-user", ROOT / "user-home", ROOT / "gradle-home"):
        folder.mkdir(parents=True, exist_ok=True)
    jdk = download(
        "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20%2B8/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20_8.tar.gz",
        "jdk17.tar.gz", "be7668bc030d578b83d6d5ef9221d6d6729bbbca8cf94a7d52e16ac68b5a5a35", "sha256",
    )
    if not (ROOT / "jdk-17.0.20+8/bin/javac").exists():
        with tarfile.open(jdk) as bundle:
            bundle.extractall(ROOT, filter="data")
    tools = download(
        "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip",
        "cmdline-tools.zip", "5fdcc763663eefb86a5b8879697aa6088b041e70",
    )
    sdk = ROOT / "android-sdk"
    if not (sdk / "cmdline-tools/19.0/bin/sdkmanager").exists():
        install_zip(tools, sdk / "cmdline-tools/19.0")
    packages = [
        ("platform-35_r02.zip", "0bb560a90a7a2cbd0dd8348224d518b638fe7949", "platforms/android-35", "android.jar"),
        ("build-tools_r35_linux.zip", "2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc", "build-tools/35.0.0", "aapt2"),
        ("platform-tools_r37.0.1-linux.zip", "477254aa5f903c15cf51001717bdf347fb6b53e0", "platform-tools", "adb"),
    ]
    # Android archive checksums are the official repository catalog's SHA-1 values.
    for filename, checksum, location, sentinel in packages:
        archive = download("https://dl.google.com/android/repository/" + filename, filename, checksum)
        if not (sdk / location / sentinel).exists():
            install_zip(archive, sdk / location)
    environment = dict(os.environ, ANDROID_HOME=str(sdk), ANDROID_USER_HOME=str(ROOT / "android-user"))
    environment["JAVA_TOOL_OPTIONS"] = f"-Duser.home={ROOT / 'user-home'}"
    if Path("/etc/ssl/certs/java/cacerts").exists():
        environment["JAVA_TOOL_OPTIONS"] += " -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
    subprocess.run(
        [str(sdk / "cmdline-tools/19.0/bin/sdkmanager"), "--sdk_root=" + str(sdk), "--licenses"],
        input="y\n" * 200, text=True, env=environment, check=True,
        stdout=subprocess.DEVNULL,
    )
    proxy = urlparse(os.environ.get("HTTPS_PROXY", os.environ.get("https_proxy", "")))
    if proxy.hostname:
        # No credentials are copied. Preserve any unrelated existing Gradle settings.
        properties = ROOT / "gradle-home/gradle.properties"
        previous = properties.read_text() if properties.exists() else ""
        names = {f"systemProp.{scheme}.proxy{part}" for scheme in ("http", "https") for part in ("Host", "Port")}
        lines = [line for line in previous.splitlines() if line.split("=", 1)[0] not in names]
        for scheme in ("http", "https"):
            lines.extend([f"systemProp.{scheme}.proxyHost={proxy.hostname}", f"systemProp.{scheme}.proxyPort={proxy.port or 80}"])
        properties.write_text("\n".join(lines) + "\n")
    print("Verified Android SDK and JDK ready. Source scripts/cloud-env.sh before building.")


if __name__ == "__main__":
    main()
