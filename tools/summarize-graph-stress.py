"""Summarize serial same-JVM MAX_FAST / Graph stress measurements, preserving failures."""
import argparse
import json
import math
from pathlib import Path
import re
import statistics

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('log', type=Path)
parser.add_argument('output', type=Path)
args = parser.parse_args()
raw = args.log.read_bytes()
encoding = 'utf-16' if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig'
groups = {}
for line in raw.decode(encoding, errors='replace').splitlines():
    if '[Graph Stress] label=' not in line:
        continue
    row = dict(re.findall(r'(\w+)=([^ ]*)', line))
    failures = re.search(r'old_failure=(.*?) graph_failure=(.*)$', line)
    row['old_failure'], row['graph_failure'] = failures.groups()
    for key in ('sample', 'amount', 'old_bytes', 'graph_bytes', 'old_raw', 'graph_raw'):
        if key in row:
            row[key] = int(row[key])
    for key in list(row):
        if key.endswith('_ms'):
            row[key] = float(row[key])
    for key in ('materials_equal', 'recipes_equal', 'bytes_equal', 'old_valid', 'graph_valid'):
        if key in row:
            row[key] = row[key] == 'true'
    groups.setdefault((row['label'], row['amount']), []).append(row)
if not groups:
    raise ValueError('No completed stress samples')
report = {}
for (label, amount), rows in groups.items():
    warm = [row for row in rows if row['sample'] > 0]
    measured = {}
    for key in ('old_wall_ms', 'old_setup_ms', 'old_run_ms', 'graph_wall_ms', 'graph_entry_ms', 'graph_solver_ms', 'graph_snapshot_ms'):
        values = sorted(row[key] for row in warm if not row['graph_failure'] and
                        (key.startswith('graph_') or not row['old_failure']))
        if values:
            measured[key] = {'median': statistics.median(values), 'p95_nearest_rank': values[math.ceil(.95*len(values))-1], 'min': values[0], 'max': values[-1]}
    name = label if len({key[1] for key in groups}) == 1 else f'{label}@{amount}'
    report[name] = {'samples': len(rows), 'first': rows[0], 'warm_samples': len(warm),
                     'all_equivalent': all(row['materials_equal'] and row['recipes_equal'] and row['bytes_equal'] for row in rows),
                     'all_interpreted_valid': all(row.get('old_valid') and row.get('graph_valid') for row in rows),
                     'all_graph_valid': all(row.get('graph_valid') for row in rows),
                     'warm': measured, 'rows': rows}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(report, indent=2), encoding='utf-8')
for label, data in report.items():
    print(label, 'samples=', data['samples'], 'equivalent=', data['all_equivalent'])
    if data['warm']:
        for key in ('old_wall_ms', 'graph_wall_ms', 'graph_solver_ms', 'graph_snapshot_ms'):
            if key in data['warm']:
                print(' ', key, data['warm'][key])
    else:
        print(' ', data['first'])
