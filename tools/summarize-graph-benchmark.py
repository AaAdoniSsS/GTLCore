"""Summarize same-server graph A/B probe output without mixing first and warm samples."""
import argparse
import json
import math
from pathlib import Path
import re
import statistics

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("log", type=Path)
parser.add_argument("output", type=Path)
args = parser.parse_args()
groups = {}
raw = args.log.read_bytes()
encoding = "utf-16" if raw.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8-sig"
for line in raw.decode(encoding, errors="replace").splitlines():
    if "[Graph A/B]" not in line:
        continue
    fields = {key: float(value) if "." in value else int(value)
              for key, value in re.findall(r"(\w+)=(\d+(?:\.\d+)?)", line)}
    label = "chain32" if "[chain32]" in line else "planks"
    groups.setdefault(label, []).append(fields)
result = {}
if not groups:
    raise ValueError("No completed Graph A/B records in log")
for label, rows in groups.items():
    warm = rows[1:]
    if not warm:
        raise ValueError(f"{label}: no warm samples")
    metrics = {}
    for key in ("baseline_setup_ms", "max_fast_ms", "graph_wall_ms", "graph_solver_ms"):
        values = sorted(row[key] for row in warm)
        metrics[key] = {"median": statistics.median(values), "p95_nearest_rank": values[math.ceil(.95 * len(values)) - 1]}
    result[label] = {"total_samples": len(rows), "first_sample": rows[0], "warm_samples": len(warm), "warm": metrics}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(result, indent=2), encoding="utf-8")
print(json.dumps(result, indent=2))
