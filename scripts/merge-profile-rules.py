#!/usr/bin/env python3

from pathlib import Path
import sys

FLAG_ORDER = "HSP"


def read_profile(path: Path):
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line:
            continue
        descriptor_start = line.find("L")
        if descriptor_start < 0:
            yield "", line
            continue
        flags = "".join(flag for flag in line[:descriptor_start] if flag in FLAG_ORDER)
        yield flags, line[descriptor_start:]


def merge_profiles(paths):
    ordered_descriptors = []
    flags_by_descriptor = {}
    for path in paths:
        for flags, descriptor in read_profile(path):
            if descriptor not in flags_by_descriptor:
                ordered_descriptors.append(descriptor)
                flags_by_descriptor[descriptor] = set()
            flags_by_descriptor[descriptor].update(flags)

    return [
        "".join(flag for flag in FLAG_ORDER if flag in flags_by_descriptor[descriptor]) + descriptor
        for descriptor in ordered_descriptors
    ]


def write_profile(path: Path, lines):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    if len(sys.argv) != 4:
        raise SystemExit(
            "usage: merge-profile-rules.py CORE_BASELINE STARTUP_PROFILE OUTPUT_DIRECTORY"
        )

    core = Path(sys.argv[1])
    startup = Path(sys.argv[2])
    output = Path(sys.argv[3])
    if not core.is_file() or not startup.is_file():
        raise SystemExit("profile input is missing")

    baseline_lines = merge_profiles([core, startup])
    startup_lines = merge_profiles([startup])
    if not baseline_lines or not startup_lines:
        raise SystemExit("generated profile is empty")

    write_profile(output / "baseline-prof.txt", baseline_lines)
    write_profile(output / "startup-prof.txt", startup_lines)
    print(
        f"baseline={len(baseline_lines)} rules, startup={len(startup_lines)} rules, "
        f"output={output}"
    )


if __name__ == "__main__":
    main()
