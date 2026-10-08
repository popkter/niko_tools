"""Decide whether this workflow run should publish a new Marketplace version."""
import json
import os
from pathlib import Path
import re
import subprocess


def read_version(properties):
    matches = re.findall(r"^\s*version\s*=\s*(\d+\.\d+\.\d+)\s*$", properties, re.MULTILINE)
    if len(matches) != 1:
        raise ValueError("gradle.properties must contain one version = X.Y.Z")
    return matches[0]


def should_publish(event_name, ref, current, previous=None, manual=False):
    if ref != "refs/heads/main":
        return False
    if event_name == "workflow_dispatch":
        return manual
    if event_name != "push" or previous is None:
        return False
    current_parts = tuple(map(int, current.split(".")))
    previous_parts = tuple(map(int, previous.split(".")))
    if current_parts < previous_parts:
        raise ValueError(f"Version must not decrease: {previous} -> {current}")
    return current_parts > previous_parts


def main():
    current = read_version(Path("gradle.properties").read_text())
    event_name = os.environ["GITHUB_EVENT_NAME"]
    ref = os.environ["GITHUB_REF"]
    previous = None
    if event_name == "push" and ref == "refs/heads/main":
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
        before = event.get("before", "")
        if re.fullmatch(r"[0-9a-f]{40}", before) and before != "0" * 40:
            properties = subprocess.check_output(["git", "show", f"{before}:gradle.properties"], text=True)
            previous = read_version(properties)
    publish = should_publish(event_name, ref, current, previous, os.environ.get("PUBLISH_MARKETPLACE") == "true")
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        output.write(f"version={current}\npublish={str(publish).lower()}\n")
    print(f"Version: {current}; previous: {previous or 'n/a'}; publish: {publish}")


if __name__ == "__main__":
    main()
