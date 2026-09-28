"""Run the real Wildcard Pattern editor through the native Forge loading chain."""

import argparse
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('smoke_server', ROOT / 'scripts/smoke-server.py')
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact', type=Path, help='Test an existing GTLCore JAR, e.g. the unpatched baseline')
    parser.add_argument('--without-wildcard', action='store_true', help='Verify startup without the optional dependency')
    args = parser.parse_args()
    artifact = args.artifact.resolve() if args.artifact else None
    if artifact is not None and not artifact.is_file():
        parser.error(f'Artifact does not exist: {artifact}')
    subprocess.run([str(ROOT / 'dev'), 'gradle', 'remapWildcardTestJar', 'devExportRuntime'], cwd=ROOT, check=True)
    additions = runtime.prepare_runtime()
    args_file = runtime.prepare_forge()
    test_mod = ROOT / 'build/dev/testmod/gtlcore-wildcard-test.jar'
    run_dir = runtime.prepare_instance(additions, prefix='dev-wildcard-',
                                       include_wildcard=not args.without_wildcard,
                                       artifact=artifact, extra_mods=[test_mod])
    console = run_dir / 'console.log'
    print(f'Running wildcard tests; log: {console}', flush=True)
    with console.open('w') as log:
        completed = subprocess.run(
            [str(Path(os.environ['JAVA_HOME']) / 'bin/java'), '-Xms1G', '-Xmx4G',
             f'-Dgtlcore.test.wildcardAbsent={str(args.without_wildcard).lower()}',
             '-Dmixin.debug.export=true', f'@{args_file}', 'nogui'],
            cwd=run_dir, stdout=log, stderr=subprocess.STDOUT, timeout=600,
        )
    report = run_dir / 'wildcard-results.json'
    if completed.returncode != 0 or not report.is_file():
        raise RuntimeError(f'Test runtime failed: exit={completed.returncode}; inspect {console}')
    results = json.loads(report.read_text())
    expected_count = 1 if args.without_wildcard else 10
    for result in results:
        print(f'{"PASS" if result["passed"] else "FAIL"}: {result["name"]} {result["detail"]}')
    if len(results) != expected_count or any(not result['passed'] for result in results):
        raise RuntimeError(f'Wildcard regression failed; results: {report}')
    debug = (run_dir / 'logs/debug.log').read_text()
    if '{gtlcore} mods' not in debug or '{gtlcore_wildcard_test} mods' not in debug:
        raise RuntimeError(f'Required mods were not discovered; inspect {console}')
    if not args.without_wildcard:
        for target in ('PropertyFilterComponent', 'FlagFilterComponent', 'SimpleIOComponent',
                       'WildcardFilterFancyConfigurator', 'WildcardIOFancyConfigurator'):
            if f'wildcard.{target}Mixin from gtlcore.mixin.json' not in debug:
                raise RuntimeError(f'Expected mixin was not applied: {target}; inspect {console}')
    if not (run_dir / 'dev-world/level.dat').is_file():
        raise RuntimeError(f'Test server did not save its world; inspect {console}')
    print(f'PASS: {len(results)} wildcard runtime tests; results: {report}')


if __name__ == '__main__':
    main()
