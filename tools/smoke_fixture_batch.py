#!/usr/bin/env python3
"""Device smoke test. Only our two empty fixture packages can be targeted.

Build/install app debug, test-target debug + second; enable AppFin Accessibility
through Settings first. This script never changes Accessibility permissions.
"""
import argparse
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--serial", required=True)
parser.add_argument("--cancel", action="store_true")
args = parser.parse_args()
base = ["adb", "-s", args.serial]
fixtures = ["dev.appfin.fixture", "dev.appfin.fixture.second"]


def adb(*command):
    result = subprocess.run(base + list(command), check=True, capture_output=True, text=True, timeout=15)
    return result.stdout


def package_stopped(package):
    output = adb("shell", "dumpsys", "package", package)
    user = next(line for line in output.splitlines() if "User 0:" in line and "stopped=" in line)
    return "stopped=true" in user


for package in fixtures:
    adb("shell", "am", "start", "-W", "-n", f"{package}/dev.appfin.fixture.TargetActivity")
    assert not package_stopped(package), f"Fixture did not start: {package}"

adb("shell", "run-as", "com.tellolabs.appfin", "rm", "-f", "cache/fixture-batch-report.txt")
command = ["shell", "am", "start", "-W", "-n", "com.tellolabs.appfin/.debug.FixtureBatchActivity"]
if args.cancel:
    command += ["--ez", "cancelImmediately", "true"]
adb(*command)
deadline = time.monotonic() + 50
report = ""
while time.monotonic() < deadline:
    try:
        report = adb("shell", "run-as", "com.tellolabs.appfin", "cat", "cache/fixture-batch-report.txt")
    except subprocess.CalledProcessError:
        time.sleep(0.2)
        continue
    if report:
        break
    time.sleep(0.2)
assert report, "No report. Check that AppFin Accessibility is connected."
print(report)
expected = "CANCELLED" if args.cancel else "STOPPED_VERIFIED"
for package in fixtures:
    assert f"{package}={expected}" in report, f"Unexpected result for {package}"
    assert package_stopped(package) != args.cancel, f"Unexpected stopped flag for {package}"
assert "error=\n" in report + "\n", "Session reported an error"
assert "AppFin progress" not in adb("shell", "dumpsys", "window", "windows"), "Progress overlay was not removed"
print("PASS: " + ("batch cancellation" if args.cancel else "two-app batch"))
