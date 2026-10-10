#!/usr/bin/env python3
"""Build a signed APK with the official Android SDK, without Gradle dependencies.

Usage: QINGTODO_ANDROID_SDK=/path/to/sdk python3 build_apk.py
Requires JDK 17 (javac) or ECJ_JAR pointing to an Eclipse compiler jar.
The generated personal keystore is retained under signing/ for future updates.
"""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent


def run(*args):
    print("Running", Path(str(args[0])).name, flush=True)
    subprocess.run([str(a) for a in args], check=True, cwd=ROOT)


def locate_sdk():
    for value in (os.environ.get("QINGTODO_ANDROID_SDK"), os.environ.get("ANDROID_SDK_ROOT"), os.environ.get("ANDROID_HOME"), str(ROOT.parent / "tooling/sdk")):
        if value and (Path(value) / "platforms").exists():
            return Path(value).resolve()
    raise SystemExit("Android SDK not found. Set QINGTODO_ANDROID_SDK.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default=str(ROOT / "build/qingtodo.apk"))
    args = parser.parse_args()
    if not os.environ.get("QINGTODO_KEYSTORE_PASSWORD"):
        raise SystemExit("Set QINGTODO_KEYSTORE_PASSWORD for your local signing key.")
    sdk = locate_sdk()
    candidates = sorted(sdk.glob("platforms/*/android.jar"))
    if not candidates:
        raise SystemExit("Install Android SDK Platform 36.")
    android = next((p for p in candidates if p.parent.name == "android-36"), candidates[-1])
    tools = sorted(p for p in (sdk / "build-tools").iterdir() if (p / "aapt2").exists())[-1]
    for name in ("aapt2", "zipalign", "apksigner", "d8"):
        p = tools / name
        if p.exists():
            p.chmod(p.stat().st_mode | 0o111)
    build = ROOT / "build"
    if build.exists():
        shutil.rmtree(build)
    for directory in ("generated", "classes", "dex"):
        (build / directory).mkdir(parents=True, exist_ok=True)
    src = ROOT / "app/src/main"
    manifest = ET.parse(src / "AndroidManifest.xml")
    manifest.getroot().set("package", "com.qingtodo.app")
    ET.register_namespace("android", "http://schemas.android.com/apk/res/android")
    manifest.write(build / "AndroidManifest.xml", encoding="utf-8", xml_declaration=True)
    run(tools / "aapt2", "compile", "--dir", src / "res", "-o", build / "resources.zip")
    run(tools / "aapt2", "link", "-o", build / "resources.apk", "-I", android, "--manifest", build / "AndroidManifest.xml", "--java", build / "generated", build / "resources.zip")
    sources = sorted((src / "java").rglob("*.java")) + sorted((build / "generated").rglob("*.java"))
    if shutil.which("javac"):
        compiler = ["javac", "-source", "8", "-target", "8"]
    else:
        ecj = Path(os.environ.get("ECJ_JAR", str(ROOT.parent / "tooling/downloads/ecj.jar")))
        if not ecj.exists():
            raise SystemExit("Install a JDK or set ECJ_JAR to an Eclipse Java compiler jar.")
        compiler = ["java", "-jar", str(ecj), "-8", "-nowarn"]
    boot_classpath = str(android)
    lambda_stubs = tools / "core-lambda-stubs.jar"
    if lambda_stubs.exists():
        boot_classpath += os.pathsep + str(lambda_stubs)
    run(*compiler, "-encoding", "UTF-8", "-bootclasspath", boot_classpath, "-d", build / "classes", *sources)
    jar = build / "classes.jar"
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as z:
        for path in sorted((build / "classes").rglob("*.class")):
            z.write(path, path.relative_to(build / "classes"))
    run("java", "-cp", tools / "lib/d8.jar", "com.android.tools.r8.D8", "--release", "--min-api", "26", "--lib", android, "--output", build / "dex", jar)
    unsigned = build / "unsigned.apk"
    shutil.copyfile(build / "resources.apk", unsigned)
    with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as z:
        for path in sorted((build / "dex").glob("*.dex")):
            z.write(path, path.name)
    aligned = build / "aligned.apk"
    run(tools / "zipalign", "-P", "16", "-f", "4", unsigned, aligned)
    signing = ROOT / "signing"
    signing.mkdir(exist_ok=True)
    key = signing / "qingtodo-personal.jks"
    if not key.exists():
        run("keytool", "-genkeypair", "-keystore", key, "-alias", "qingtodo", "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000", "-storepass:env", "QINGTODO_KEYSTORE_PASSWORD", "-keypass:env", "QINGTODO_KEYSTORE_PASSWORD", "-dname", "CN=QingTodo Personal, OU=Personal App, O=QingTodo, C=CN")
        key.chmod(0o600)
    output = Path(args.output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    run("java", "-jar", tools / "lib/apksigner.jar", "sign", "--ks", key, "--ks-key-alias", "qingtodo", "--ks-pass", "env:QINGTODO_KEYSTORE_PASSWORD", "--key-pass", "env:QINGTODO_KEYSTORE_PASSWORD", "--out", output, aligned)
    run("java", "-jar", tools / "lib/apksigner.jar", "verify", "--verbose", output)
    run(tools / "zipalign", "-c", "-P", "16", "4", output)
    print("APK:", output, "bytes:", output.stat().st_size)


if __name__ == "__main__":
    main()
