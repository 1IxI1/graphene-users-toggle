#!/usr/bin/env python3
"""Build a dependency-free Android APK with the installed SDK (no Gradle/network)."""
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
sdk_path = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
if not sdk_path:
    raise SystemExit("Set ANDROID_SDK_ROOT or ANDROID_HOME to your Android SDK directory")
SDK = Path(sdk_path)
TOOLS = SDK / "build-tools/35.0.0"
ANDROID = SDK / "platforms/android-35/android.jar"
BUILD = ROOT / "build"
MAIN = ROOT / "app/src/main"


def run(*args):
    print("+", " ".join(str(arg) for arg in args), flush=True)
    subprocess.run([str(arg) for arg in args], cwd=ROOT, check=True)


assert ANDROID.is_file(), f"Missing Android 35 platform: {ANDROID}"
assert (TOOLS / "aapt2").is_file(), f"Missing build tools: {TOOLS}"
for name in ("generated", "classes", "dex", "tests"):
    if (BUILD / name).exists():
        shutil.rmtree(BUILD / name)
    (BUILD / name).mkdir(parents=True, exist_ok=True)

run("javac", "--release", "8", "-d", BUILD / "tests",
    MAIN / "java/local/usertap/TapSequence.java",
    ROOT / "tests/GestureTest.java")
run("java", "-cp", BUILD / "tests", "GestureTest")
run(TOOLS / "aapt2", "compile", "--dir", MAIN / "res", "-o", BUILD / "compiled-res.zip")
run(TOOLS / "aapt2", "link", "-I", ANDROID, "--manifest", MAIN / "AndroidManifest.xml",
    "--java", BUILD / "generated", "-o", BUILD / "resources.apk", BUILD / "compiled-res.zip")
sources = sorted((MAIN / "java").rglob("*.java")) + sorted((BUILD / "generated").rglob("*.java"))
run("javac", "--release", "8", "-encoding", "UTF-8", "-classpath", ANDROID,
    "-d", BUILD / "classes", *sources)
with zipfile.ZipFile(BUILD / "classes.jar", "w", zipfile.ZIP_DEFLATED) as jar:
    for path in sorted((BUILD / "classes").rglob("*.class")):
        jar.write(path, path.relative_to(BUILD / "classes"))
run(TOOLS / "d8", "--lib", ANDROID, "--min-api", "31", "--output", BUILD / "dex", BUILD / "classes.jar")
shutil.copyfile(BUILD / "resources.apk", BUILD / "unsigned.apk")
with zipfile.ZipFile(BUILD / "unsigned.apk", "a", zipfile.ZIP_DEFLATED) as apk:
    for dex in sorted((BUILD / "dex").glob("*.dex")):
        apk.write(dex, dex.name)
run(TOOLS / "zipalign", "-f", "4", BUILD / "unsigned.apk", BUILD / "aligned.apk")
signing = ROOT / "signing"
signing.mkdir(exist_ok=True)
keystore = signing / "local.keystore"
if not keystore.exists():
    run("keytool", "-genkeypair", "-keystore", keystore, "-storepass", "android",
        "-keypass", "android", "-alias", "usertap", "-keyalg", "RSA", "-keysize", "3072",
        "-validity", "10000", "-dname", "CN=User Tap local development", "-noprompt")
    keystore.chmod(0o600)
run(TOOLS / "apksigner", "sign", "--ks", keystore, "--ks-pass", "pass:android",
    "--key-pass", "pass:android", "--out", BUILD / "user-tap.apk", BUILD / "aligned.apk")
run(TOOLS / "apksigner", "verify", "--verbose", BUILD / "user-tap.apk")
print(f"APK: {BUILD / 'user-tap.apk'}")
