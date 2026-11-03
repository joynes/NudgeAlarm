"""Report honest, whole-app JaCoCo line coverage; fail below the requested target."""
import argparse
import os
from pathlib import Path
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("report", type=Path)
parser.add_argument("--minimum", type=float, default=95)
args = parser.parse_args()
root = ET.parse(args.report).getroot()
counter = next(c for c in root.findall("counter") if c.attrib["type"] == "LINE")
covered, missed = int(counter.attrib["covered"]), int(counter.attrib["missed"])
total = covered + missed
if not total:
    raise SystemExit("Coverage report contains no executable lines")
percentage = covered * 100 / total
lines = [f"Line coverage: {percentage:.2f}% ({covered}/{total}); target {args.minimum:g}%", "", "Largest uncovered source files:"]
sources = []
for package in root.findall("package"):
    for source in package.findall("sourcefile"):
        count = next((c for c in source.findall("counter") if c.attrib["type"] == "LINE"), None)
        if count is None:  # Interfaces without executable code have no line counter.
            continue
        sources.append((int(count.attrib["missed"]), package.attrib["name"] + "/" + source.attrib["name"]))
lines.extend(f"- {name}: {missing} uncovered lines" for missing, name in sorted(sources, reverse=True)[:20])
summary = "\n".join(lines) + "\n"
print(summary)
if os.environ.get("GITHUB_STEP_SUMMARY"):
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as output:
        output.write(summary)
if percentage < args.minimum:
    raise SystemExit(1)
