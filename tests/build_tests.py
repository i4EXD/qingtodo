#!/usr/bin/env python3
"""Run from the project root after building the main APK with build_apk.py."""
from pathlib import Path
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent.parent
if not os.environ.get('QINGTODO_KEYSTORE_PASSWORD'):
    raise SystemExit('Set QINGTODO_KEYSTORE_PASSWORD for your local signing key.')
sdk = Path(os.environ.get('QINGTODO_ANDROID_SDK', str(ROOT.parent / 'tooling/sdk')))
tools = sorted(p for p in (sdk / 'build-tools').iterdir() if (p / 'aapt2').exists())[-1]
android = sdk / 'platforms/android-36/android.jar'
work = ROOT / 'build/tests'
work.mkdir(parents=True, exist_ok=True)
for d in ['classes', 'dex']:
    shutil.rmtree(work / d, ignore_errors=True)
    (work / d).mkdir()

def run(*args): subprocess.run([str(a) for a in args], check=True)

run(tools / 'aapt2', 'link', '-o', work / 'test-res.apk', '-I', android, '--manifest', ROOT / 'tests/AndroidManifest.xml')
ecj = os.environ.get('ECJ_JAR', str(ROOT.parent / 'tooling/downloads/ecj.jar'))
run('java', '-jar', ecj, '-8', '-nowarn', '-encoding', 'UTF-8', '-bootclasspath', str(android) + os.pathsep + str(tools / 'core-lambda-stubs.jar'), '-classpath', ROOT / 'build/classes', '-d', work / 'classes', *sorted((ROOT / 'tests').glob('*.java')))
with zipfile.ZipFile(work / 'tests.jar', 'w', zipfile.ZIP_DEFLATED) as z:
    for p in (work / 'classes').rglob('*.class'): z.write(p, p.relative_to(work / 'classes'))
run('java', '-cp', tools / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '26', '--lib', android, '--classpath', ROOT / 'build/classes.jar', '--output', work / 'dex', work / 'tests.jar')
shutil.copyfile(work / 'test-res.apk', work / 'test-unsigned.apk')
with zipfile.ZipFile(work / 'test-unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as z:
    for p in (work / 'dex').glob('*.dex'): z.write(p, p.name)
run(tools / 'zipalign', '-f', '4', work / 'test-unsigned.apk', work / 'test-aligned.apk')
run('java', '-jar', tools / 'lib/apksigner.jar', 'sign', '--ks', ROOT / 'signing/qingtodo-personal.jks', '--ks-pass', 'env:QINGTODO_KEYSTORE_PASSWORD', '--out', work / 'qingtodo-acceptance.apk', work / 'test-aligned.apk')
print(work / 'qingtodo-acceptance.apk')
